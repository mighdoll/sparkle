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