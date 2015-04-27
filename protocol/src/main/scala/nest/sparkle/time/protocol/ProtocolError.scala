package nest.sparkle.time.protocol

import nest.sparkle.util.Log
import spray.json.DeserializationException
import spray.json._
import nest.sparkle.store.{ColumnNotFound}
import nest.sparkle.time.transform.InvalidPeriod
import nest.sparkle.time.protocol.ResponseJson.StatusMessageFormat
import spray.json.DefaultJsonProtocol._
import nest.sparkle.time.protocol.ResponseJson.{ StatusMessageFormat }
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat

/** converting internal processing errors into protocol messages */
object ProtocolError extends Log {

  /** translate errors in processing to appropriate Status messages to the client */
  def streamErrorMessage(request: StreamRequestMessage, error: Throwable): StatusMessage = {
    val requestAsString = request.toJson.compactPrint
    val status =
      error match {
        case ColumnNotFound(msg) =>
          Status(601, s"Column not found.  $msg request: $requestAsString")
        case InvalidPeriod(msg) =>
          Status(603, s"Invalid period in Transform parameter.  $msg request: $requestAsString")
        case err: MalformedSourceSelector =>
          Status(603, s"parameter error in transform.  request: $requestAsString")
        case err: DeserializationException =>
          Status(604, s"parameter error in source selector.  request: $requestAsString")
        case CustomSourceNotFound(msg) =>
          Status(605, s"custom source selector not found: $msg.  request: $requestAsString")
        case AuthenticationFailed =>
          Status(611, s"Authentication failed.  request: $requestAsString")
        case AuthenticationMissing =>
          Status(612, s"Authentication missing.  request: $requestAsString")
        case ColumnForbidden(msg) =>
          Status(613, s"Access to column forbidden.  $msg request: $requestAsString")
        case err =>
          log.error("unexpected error processing request", err)
          Status(999, s"unknown error $err in $requestAsString")
      }
    val realmToClient = request.realm.map { orig => RealmToClient(orig.name) }
    val statusMessage = StatusMessage(requestId = request.requestId, realm = realmToClient,
      traceId = request.traceId, messageType = MessageType.Status, message = status)
    log.warn(s"streamError ${status.code} ${status.description}: request: $requestAsString ")
    statusMessage
  }
}
