package nest.sparkle.time.protocol

import spray.json._

import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.MilliTime

/** Spray json converters for transform parameters */
object TransformParametersJson extends DefaultJsonProtocol {
  implicit def SummarizeParamsFormat[T: JsonFormat]: RootJsonFormat[SummarizeParams[T]] = jsonFormat4(SummarizeParams.apply[T])
}

/** Spray json encoder/decoder for JsonStreamType */
object JsonStreamTypeFormat extends DefaultJsonProtocol {
  implicit object JsonStreamTypeFormatter extends JsonFormat[JsonStreamType] {
    def write(jsonStreamType: JsonStreamType): JsValue = JsString(jsonStreamType.name)

    def read(value: JsValue): JsonStreamType = value match {
      case JsString(KeyValueType.name) => KeyValueType
      case JsString(ValueType.name)    => ValueType
      case _                           => throw new DeserializationException("JsonStreamType expected")
    }
  }
}

/** spray json converters for response messages */
object ResponseJson extends DefaultJsonProtocol {
  import JsonStreamTypeFormat._
  implicit val StreamFormat = jsonFormat5(Stream)
  implicit val StreamsFormat = jsonFormat1(Streams)
  implicit val StreamsMessageFormat = jsonFormat5(StreamsMessage)
}

/** spray json converters for request messages */
object RequestJson extends DefaultJsonProtocol {
  implicit val StreamRequestFormat = jsonFormat5(StreamRequest)
  implicit val StreamRequestMessageFormat = jsonFormat5(StreamRequestMessage)
  implicit val CustomSelectorFormat = jsonFormat2(CustomSelector)
}

/** spray json converters for MilliTime */
object TimeJson extends DefaultJsonProtocol {
  implicit object MilliTimeFormat extends JsonFormat[MilliTime] {
    def write(milliTime: MilliTime): JsValue = JsNumber(milliTime.millis)
    def read(value: JsValue): MilliTime = value match {
      case JsNumber(millis) => MilliTime(millis.toLong)
      case _                => throw new DeserializationException("MilliTime expected")
    }
  }
}

/** spray json converters for Event */
object EventJson extends DefaultJsonProtocol {
  implicit def EventFormat[T: JsonFormat, U: JsonFormat]: JsonFormat[Event[T, U]] = { // SCALA, avoid the def here
    val argumentFormat = implicitly[JsonFormat[T]]
    val valueFormat = implicitly[JsonFormat[U]]

    new JsonFormat[Event[T, U]] {
      def write(event: Event[T, U]): JsValue = {
        JsArray(argumentFormat.write(event.argument), valueFormat.write(event.value))
      }

      def read(value: JsValue): Event[T, U] = value match {
        case JsArray(Seq(elem1, elem2)) =>
          val argument = argumentFormat.read(elem1)
          val value = valueFormat.read(elem2)
          Event(argument, value)
        case _ => throw new DeserializationException("JsonStreamType expected")

      }
    }
  }
}
