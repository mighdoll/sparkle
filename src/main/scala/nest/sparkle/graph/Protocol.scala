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

package nest.sparkle.graph

import spray.json.DefaultJsonProtocol
import spray.json.JsArray
import spray.json.JsObject
import spray.json.JsonFormat

case class DataRequest(request: String, parameters: JsObject)

/** spray json converters for objects we send/receive to the browser */
object DataProtocolJson extends DefaultJsonProtocol {
  implicit val DataRequestFormat = jsonFormat2(DataRequest)
}

case class SummarizeParams[T](
  dataSet: String,  
  column: String,
  maxResults: Int,
  start: Option[T] = None,
  end: Option[T] = None,
  edgeExtra: Option[Boolean] = None)
  
/** spray json converters for request parameters */
object ParametersJson extends DefaultJsonProtocol {
  implicit def SummarizeParamsFormat[T: JsonFormat] = jsonFormat6(SummarizeParams.apply[T])
}

case class Stream(id:Long, data:Array[JsArray]) // LATER more type trickiness for array contents 

case class SingleStreamResponse(stream:Stream)

object ResponseJson extends DefaultJsonProtocol {
  implicit val StreamFormat = jsonFormat2(Stream)
  implicit val SingleStreamResponseFormat = jsonFormat1(SingleStreamResponse)
}
