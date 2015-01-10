package nest.sparkle.time.server

import com.typesafe.config.Config
import spray.json._
import unfiltered.netty.websockets._
import nest.sparkle.store.Store
import nest.sparkle.time.protocol.{ StreamRequestApi, StreamRequestMessage }
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.{ StreamsMessageFormat, StatusMessageFormat }
import nest.sparkle.util.Log
import scala.concurrent.ExecutionContext
import nest.sparkle.time.protocol.StreamsMessage
import nest.sparkle.time.protocol.MessageType
import akka.actor.ActorSystem
import nest.sparkle.time.protocol.ServerResponse
import nest.sparkle.measure.Measurements

class DataWebSocket(store: Store, rootConfig: Config) // format: OFF
    (implicit system: ActorSystem, measurements:Measurements) extends Log { // format: ON
  import system.dispatcher
  var sockets = new scala.collection.mutable.ListBuffer[WebSocket]()
  val webSocketServer = new WebSocketServer()
  val api = StreamRequestApi(store, rootConfig)

  webSocketServer.results.subscribe { x =>
    x match {
      case Open(socket) => log.info("socket open")
      case Message(socket, Text(message)) =>
        log.info(s"message received: $message")
        handleMessage(socket, message)
      case x => log.error(s"unhandled message: $x")
    }
  }

  webSocketServer.start()

  def shutdown() {
    webSocketServer.shutdown()
  }

  private def handleMessage(socket: WebSocket, message: String) {
    val json = message.asJson

    val request = json.convertTo[StreamRequestMessage](StreamRequestMessageFormat) // TODO error handling
    api.socketStreamRequest(request, socket)
  }

}


