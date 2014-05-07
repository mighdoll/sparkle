package nest.sparkle.time.transform

import scala.concurrent.ExecutionContext
import spray.json._
import nest.sparkle.store.Column
import nest.sparkle.time.protocol.{ JsonDataStream, JsonEventWriter, KeyValueType }
import rx.lang.scala.Observable
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
      Transform.rangeParameters(transformParameters)(keyFormat).map { range =>
        val events = column.readRange(range.start, range.end)
        val jsonData = JsonEventWriter.fromObservable(events)
        JsonDataStream(
          dataStream = jsonData,
          streamType = KeyValueType
        )
      }
    }

    tryJsonDataStream.recover { case err => JsonDataStream.error(err) } get
  }
}
