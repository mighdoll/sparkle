/* Copyright 2014  Nest Labs

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

import rx.lang.scala.Observable
import spray.json._

/** Contains an observable stream of json encoded arrays suitable for use in a protocol json data stream.  */
case class JsonDataStream( // format: OFF
  /** An observable that produces json encoded data to send in Streams or Update messages
    * Each JsArray corresponds to one Datum, ready to serialize to a protocol message.
    * Each Seq is normally sent in separate protocol message. The first Seq is sent
    * in a Streams message, and each subsequent Seq in an Update message.
    */
  dataStream: Observable[Seq[JsArray]],

  /** Format of the produced JsArrays */
  streamType: JsonStreamType,

  /** Optional name for the stream so that transforms that return multiple streams can label each stream */
  metadata: Option[String] = None
) // format: ON

object JsonDataStream {
  /** return a stream that immediately generates an error (and no other data) */
  def error(err: Throwable): JsonDataStream = {
    JsonDataStream(Observable.error(err), KeyValueType)
  }

  /** a JsonDataStream containing no data */
  def empty: JsonDataStream =
    JsonDataStream(
      dataStream = Observable.empty,
      streamType = KeyValueType,
      metadata = None)

}