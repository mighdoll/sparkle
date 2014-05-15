package nest.sparkle.time.transform

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.control.Exception.nonFatalCatch

import spray.json._

import rx.lang.scala.Observable

import nest.sparkle.store.{Column, Event}
import nest.sparkle.time.protocol.{JsonDataStream, JsonEventWriter, KeyValueType, RawParameters}
import nest.sparkle.time.protocol.TransformParametersJson.RawParametersFormat
import nest.sparkle.util.RecoverJsonFormat


/** match the "Raw" transform, and just copy the column input to the output */
object RawTransform {
  def unapply(transform: String): Option[ColumnTransform] = {
    transform.toLowerCase match {
      case "raw" => Some(JustCopy)
      case _     => None
    }
  }
}


/** a transform that copies the array of source columns to json encodable output streams */
object JustCopy extends ColumnTransform  {
  override def apply[T,U] ( // format: OFF
       column: Column[T,U],
       transformParameters: JsObject
     ) (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    implicit val keyFormat = RecoverJsonFormat.jsonFormat[T](column.keyType)
    implicit val valueFormat = RecoverJsonFormat.jsonFormat[U](column.valueType)
    
    val tryJsonDataStream = {
      parseRawParameters(transformParameters)(keyFormat).map { raw =>
        val fetched:Seq[IntervalAndEvents[T,U]] = SelectRanges.fetchRanges(column, raw.ranges)
        val eventsSeq = fetched.map { case(IntervalAndEvents(intervalOpt, events)) => events}
        val events = eventsSeq.reduce{(a,b) => a ++ b}
        val jsonData = JsonEventWriter.fromObservable(events)
        JsonDataStream(
          dataStream = jsonData,
          streamType = KeyValueType
        )
      }
    }

    tryJsonDataStream.recover { case err => JsonDataStream.error(err) } get
  }
    
  private def parseRawParameters[T: JsonFormat](transformParameters: JsObject):Try[RawParameters[T]] = {
    nonFatalCatch.withTry { transformParameters.convertTo[RawParameters[T]] }
  }
}
