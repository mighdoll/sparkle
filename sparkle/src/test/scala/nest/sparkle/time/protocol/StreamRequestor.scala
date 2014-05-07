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
import spray.json._
import nest.sparkle.time.protocol.TransformParametersJson.RangeParamsFormat
import nest.sparkle.time.protocol.RequestJson.CustomSelectorFormat
import spray.json.DefaultJsonProtocol._

sealed trait TestSelector
case class SelectString(columnPath: String) extends TestSelector
case class SelectCustom(customSelector: CustomSelector) extends TestSelector

trait StreamRequestor {
  var currentRequestId = 0
  def defaultColumnPath:String = "defaultTestColumn"
  /** return a request id and trace id for a new protocol request */
  def nextRequestIds(): (Int, String) = synchronized {
    currentRequestId = currentRequestId + 1
    (currentRequestId, "trace-" + currentRequestId.toString)
  }

  private val defaultRange = RangeParameters[Long](maxResults = 10)

  /** return a new StreamRequestMessage */
  def streamRequest[T: JsonFormat](transform: String, selector: TestSelector = SelectString(defaultColumnPath), // format: OFF
      range: RangeParameters[T] = defaultRange): StreamRequestMessage = {  // format: ON
    val (requestId, traceId) = nextRequestIds()
    val paramsJson = range.toJson.asJsObject 

    val sources = selector match {
      case SelectString(columnPath) => Array(columnPath.toJson)
      case SelectCustom(custom)     => Array(custom.toJson)
    }

    val streamRequest = StreamRequest(sendUpdates = None, itemLimit = None, sources = sources, transform = transform, paramsJson)

    StreamRequestMessage(requestId = Some(requestId),
      realm = None, traceId = Some(traceId), messageType = "StreamRequest", message = streamRequest)
  }

}
