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
import nest.sparkle.time.protocol.TransformParametersJson.SummaryParametersFormat
import nest.sparkle.time.protocol.RequestJson.CustomSelectorFormat
import spray.json.DefaultJsonProtocol._

sealed trait TestSelector
case class SelectString(columnPath: String) extends TestSelector
case class SelectCustom(customSelector: CustomSelector) extends TestSelector

trait StreamRequestor {
  var currentRequestId = 0
  def defaultColumnPath: String = "defaultTestColumn"
    
  /** return a request id and trace id for a new protocol request */
  def nextRequestIds(): (Int, String) = synchronized {
    currentRequestId = currentRequestId + 1
    (currentRequestId, "trace-" + currentRequestId.toString)
  }

  private val defaultSummaryParameters = SummaryParameters[Long](partByCount = Some(10))

  /** return a new StreamRequestMessage for a summary transform, summarizing the full range into one partition */
  def summaryRequestOne[T: JsonFormat](transform: String, selector: TestSelector = SelectString(defaultColumnPath)) // format: OFF
      : StreamRequestMessage = { // format: ON
    summaryRequest[T](transform, selector, SummaryParameters())
  }

  /** return a new StreamRequestMessage for a summary transform */
  def summaryRequest[T: JsonFormat](transform: String, selector: TestSelector = SelectString(defaultColumnPath), // format: OFF
      params: SummaryParameters[T] = defaultSummaryParameters): StreamRequestMessage = { // format: ON
    streamRequest[SummaryParameters[T]](transform, params, selector)
  }

  /** return a new StreamRequest for an arbitrary transform */
  def streamRequest[U: JsonFormat]( // format: OFF
      transform: String, params: U, selector: TestSelector = SelectString(defaultColumnPath)) 
      : StreamRequestMessage = { // format: ON
    val (requestId, traceId) = nextRequestIds()
    val paramsJson = params.toJson.asJsObject

    val sources = selector match {
      case SelectString(columnPath) => Array(columnPath.toJson)
      case SelectCustom(custom)     => Array(custom.toJson)
    }

    val streamRequest = StreamRequest(sendUpdates = None, itemLimit = None, sources = sources, transform = transform, paramsJson)

    StreamRequestMessage(requestId = Some(requestId),
      realm = None, traceId = Some(traceId), messageType = "StreamRequest", message = streamRequest)
  }

  /** return a string StreamRequest for a simple transform */
  def stringRequest(columnPath:String, transform:String,
                    transformParameters:String = "{}"): String = {
    s"""{
    |  "messageType": "StreamRequest",
    |  "message": {
    |    "sources": [
  	|  	  "$columnPath"
    |    ],
    |    "transform": "$transform",
    |    "transformParameters": $transformParameters
    |  }
    |}""".stripMargin
  }

}
