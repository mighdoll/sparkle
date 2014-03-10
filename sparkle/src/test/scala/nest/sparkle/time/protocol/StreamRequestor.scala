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
import nest.sparkle.time.protocol.TransformParametersJson.SummarizeParamsFormat
import spray.json.DefaultJsonProtocol._

trait StreamRequestor {
  self: TestStore =>
    
  var currentRequestId = 0
  
  /** return a request id and trace id for a new protocol request */
  def nextRequestIds(): (Int, String) = synchronized {
    currentRequestId = currentRequestId + 1
    (currentRequestId, "trace-" + currentRequestId.toString)
  }

  /** return a new StreamRequestMessage */
  def streamRequest(transform: String, columnPath: String = testColumnPath, maxResults: Int = 10): StreamRequestMessage = {
    val summarizeParams = SummarizeParams[Long](maxResults = maxResults)

    val (requestId, traceId) = nextRequestIds()
    val paramsJson = summarizeParams.toJson(SummarizeParamsFormat[Long](LongJsonFormat)).asJsObject // SCALA (spray-json) can this be less explicit?

    val sources = Array(columnPath.toJson)
    val streamRequest = StreamRequest(sendUpdates = None, itemLimit = None, sources = sources, transform = transform, paramsJson)

    StreamRequestMessage(requestId = Some(requestId),
      realm = None, traceId = Some(traceId), messageType = "StreamRequest", message = streamRequest)
  }

}
