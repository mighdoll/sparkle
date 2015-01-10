package nest.sparkle.time.transform

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.control.Exception.nonFatalCatch
import spray.json._
import rx.lang.scala.Observable
import nest.sparkle.store.{ Column, Event }
import nest.sparkle.time.protocol.{ JsonDataStream, JsonEventWriter, KeyValueType, RawParameters }
import nest.sparkle.time.protocol.TransformParametersJson.RawParametersFormat
import nest.sparkle.util.RecoverJsonFormat
import nest.sparkle.store.OngoingEvents

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
object JustCopy extends ColumnTransform {
  override def apply[T, U]( // format: OFF
       column: Column[T,U],
       transformParameters: JsObject
     ) (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    implicit val keyFormat = RecoverJsonFormat.jsonFormat[T](column.keyType)
    implicit val valueFormat = RecoverJsonFormat.jsonFormat[U](column.valueType)

    /** Return a json data stream containing first the initial set of event
     *  data in a single block, followed by the ongoing event data.  
     *  Note: the eventsSeq sequence contains one element for each RangeInterval */
    def jsonStream(eventsSeq: Seq[OngoingEvents[T, U]]): JsonDataStream = {
      val initialEventsSeq = eventsSeq.map(_.initial)
      val initialEvents = initialEventsSeq.reduce{ (a, b) => a ++ b }
      val initialJsonData = JsonEventWriter.fromObservableSingle(initialEvents)

      val ongoingEventsSeq = eventsSeq.map(_.ongoing)
      val ongoingEvents = ongoingEventsSeq.reduce{ (a, b) => a ++ b }
      val ongoingJsonData = JsonEventWriter.fromObservableMulti(ongoingEvents)
      
      val jsonData = initialJsonData ++ ongoingJsonData
      JsonDataStream(
        dataStream = jsonData,
        streamType = KeyValueType
      )
    }

    val tryJsonDataStream = {
      parseRawParameters(transformParameters)(keyFormat).map { raw =>
        val fetched: Seq[IntervalAndEvents[T, U]] = SelectRanges.fetchRanges(column, raw.ranges)
        val eventsSeq = fetched.map { case (IntervalAndEvents(intervalOpt, events)) => events }
        jsonStream(eventsSeq)
      }
    }

    tryJsonDataStream.recover { case err => JsonDataStream.error(err) } get
  }

  private def parseRawParameters[T: JsonFormat](transformParameters: JsObject): Try[RawParameters[T]] = {
    nonFatalCatch.withTry { transformParameters.convertTo[RawParameters[T]] }
  }
}
