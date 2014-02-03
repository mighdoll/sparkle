/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.time.protocol

import nest.sparkle.store.cassandra.MilliTime
import spray.json._
import nest.sparkle.store.Event

/** spray json converters for json objects we send or receive */

/** message wrapper.  to or from the server */
trait DistributionMessage[T] {
  val requestId: Option[Long]
  val realm: Option[String]
  val traceId: Option[String]
  val messageType: String
  val message: T
}

/** distribution layer message type */
object MessageType {
  val StreamRequest = "StreamRequest"
  val Streams = "Streams"
}

/** request a transformed stream.  sent from from client to server */
case class StreamRequest(sendUpdates: Option[Boolean], itemLimit: Option[Long], sources: Array[JsValue],
                         transform: String, transformParameters: JsObject)

/** a StreamRequest inside a DistributionMessage */
case class StreamRequestMessage(requestId: Option[Long], realm: Option[String], traceId: Option[String],
                                messageType: String, message: StreamRequest) extends DistributionMessage[StreamRequest]


/** a Stream inside a Distribution Message */
case class StreamsMessage(requestId: Option[Long], realm: Option[String], traceId: Option[String],
                          messageType: String, message: Streams) extends DistributionMessage[Streams]

/** Limit the flow of data over a stream temporarily.  Sent from client to Server */
case class StreamControl(streamId: Long, maxItems: Long)

/** Stop a stream.  Sent from client to server.*/
case class StreamDrop(streamId: Long)

/** Report errors and other status events.  Sent from server to client. */
case class Status(code: Long, description: String)

/** Parameters for summarize transforms */
case class SummarizeParams[T](
  maxResults: Int,
  start: Option[T] = None,
  end: Option[T] = None,
  edgeExtra: Option[Boolean] = None)

/** Spray json converters for transform parameters */
object TransformParametersJson extends DefaultJsonProtocol {
  implicit def SummarizeParamsFormat[T: JsonFormat] = jsonFormat4(SummarizeParams.apply[T])
}

/** Start some data streams.  Sent from server to client */
case class Streams(streams: Array[Stream])

/** Description of the head of a data stream.  Sent from server to client as part of a Streams message.  */
case class Stream(streamId: Long, source: JsValue, label: Option[JsValue], streamType: JsonStreamType,
                  data: Option[Seq[JsArray]], end: Option[Boolean])


/** Type of the json data stream: keyValue or just value */
sealed abstract class JsonStreamType(val name: String)
case object KeyValueType extends JsonStreamType("KeyValue")
case object ValueType extends JsonStreamType("Value")

/** Spray json encoder/decoder for JsonStreamType */
object JsonStreamTypeFormat extends DefaultJsonProtocol {
  implicit object JsonStreamTypeFormatter extends JsonFormat[JsonStreamType] {
    def write(jsonStreamType: JsonStreamType) = JsString(jsonStreamType.name)

    def read(value: JsValue) = value match {
      case JsString(KeyValueType.name) => KeyValueType
      case JsString(ValueType.name)    => ValueType
      case _                           => throw new DeserializationException("JsonStreamType expected")
    }
  }
}
import JsonStreamTypeFormat.JsonStreamTypeFormatter

/** spray json converters for request messages */
object RequestJson extends DefaultJsonProtocol {
  implicit val StreamRequestFormat = jsonFormat5(StreamRequest)
  implicit val StreamRequestMessageFormat = jsonFormat5(StreamRequestMessage)
}

/** spray json converters for response messages */
object ResponseJson extends DefaultJsonProtocol {
  implicit val StreamFormat = jsonFormat6(Stream)
  implicit val StreamsFormat = jsonFormat1(Streams)
  implicit val StreamsMessageFormat = jsonFormat5(StreamsMessage)
}

/** spray json converters for MilliTime */
object TimeJson extends DefaultJsonProtocol {
  implicit object MilliTimeFormat extends JsonFormat[MilliTime] {
    def write(milliTime: MilliTime) = JsNumber(milliTime.millis)
    def read(value: JsValue) = value match {
      case JsNumber(millis) => MilliTime(millis.toLong)
      case _                => throw new DeserializationException("MilliTime expected")
    }
  }
}

/** spray json converters for Event */
object EventJson extends DefaultJsonProtocol {
  implicit def EventFormat[T: JsonFormat, U: JsonFormat]: JsonFormat[Event[T, U]] = {   // SCALA, avoid the def here
    val argumentFormat = implicitly[JsonFormat[T]]
    val valueFormat = implicitly[JsonFormat[U]]

    new JsonFormat[Event[T, U]] {
      def write(event: Event[T, U]) = {
        JsArray(argumentFormat.write(event.argument), valueFormat.write(event.value))
      }

      def read(value: JsValue) = value match {
        case JsArray(Seq(elem1, elem2)) =>
          val argument = argumentFormat.read(elem1)
          val value = valueFormat.read(elem2)
          Event(argument, value)
        case _ => throw new DeserializationException("JsonStreamType expected")

      }
    }
  }
}

