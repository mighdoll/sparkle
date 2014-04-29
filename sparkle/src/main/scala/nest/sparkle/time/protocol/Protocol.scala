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

import spray.json._

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

case class CustomSelector(selector:String, selectorParameters:JsObject)

/** Parameters for summarize transforms */
case class SummarizeParams[T](
  maxResults: Int,
  start: Option[T] = None,
  end: Option[T] = None,
  edgeExtra: Option[Boolean] = None)


/** Start some data streams.  Sent from server to client */
case class Streams(streams: Array[Stream])

/** Description of the head of a data stream.  Sent from server to client as part of a Streams message.  */
case class Stream(streamId: Long, metadata: Option[JsValue], streamType: JsonStreamType,
                  data: Option[Seq[JsArray]], end: Option[Boolean])

/** Type of the json data stream: keyValue or just value */
abstract class JsonStreamType(val name: String)
case object KeyValueType extends JsonStreamType("KeyValue")
case object ValueType extends JsonStreamType("Value")




