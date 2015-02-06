package nest.sparkle.time.server

import com.typesafe.config.Config

import akka.actor.ActorSystem

import nest.sparkle.measure.ConfiguredMeasurements
import nest.sparkle.store.Store
import nest.sparkle.test.PortsTestFixture
import nest.sparkle.util.ConfigUtil._

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
}
