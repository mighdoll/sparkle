package nest.sparkle.time.server

import scala.concurrent.Promise
import scala.util.Success
import com.typesafe.config.Config

import akka.actor.ActorSystem

import nest.sparkle.measure.ConfiguredMeasurements
import nest.sparkle.store.Store
import nest.sparkle.test.PortsTestFixture
import nest.sparkle.util.ConfigUtil._
import nest.sparkle.util.FutureAwait.Implicits._
import scala.concurrent.duration._

object DataWebSocketFixture {
  def withDataWebSocket[T]
      ( rootConfig: Config, store:Store )
      ( fn: Int => T )
      ( implicit system:ActorSystem)
      : T = {
    import system.dispatcher
    val port = PortsTestFixture.takePort()
    val configWithPorts = modifiedConfig(rootConfig,
      "sparkle.web-socket.port" -> port
    )
    implicit val measurements = new ConfiguredMeasurements(configWithPorts)
    val webSocket = new DataWebSocket(store, configWithPorts)
    try {
      fn(port)
    } finally {
      webSocket.shutdown()
    }
  }

  def withWebSocketRequest[T]
      ( rootConfig: Config, store:Store, message:String, responseCount: Int,
        awaitTest: FiniteDuration = 90.seconds)
      ( fn: Seq[String] => T )
      ( implicit system:ActorSystem)
      : T = {
    var receivedCount = 0
    val received = Vector.newBuilder[String]
    val finished = Promise[Unit]()
    withDataWebSocket(rootConfig, store) { port =>
      tubesocks.Sock.uri(s"ws://localhost:$port/data") {
        case tubesocks.Open(s) =>
          s.send(message)
        case tubesocks.Message(text, socket) =>
          received += text
          receivedCount += 1
          if (receivedCount == responseCount) {
            finished.complete(Success(Unit))
          }
      }
      finished.future.await(awaitTest)
      fn(received.result)
    }
  }

}
