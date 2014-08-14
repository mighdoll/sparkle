package nest.sg

import com.typesafe.config.ConfigFactory

import nest.sparkle.time.server.ServerLaunch
import nest.sparkle.util.ConfigUtil.modifiedConfig
import nest.sparkle.util.ConfigureLogback

/** launch a sparkle server for a console based debugging app */
trait ConsoleServer {

  private def configOverrides: List[(String, Any)] = List(
    "sparkle-time-server.port" -> 2323,
    "sparkle-time-server.web-root.resource" -> List("web/sg/plot-default")
//    "sparkle-time-server.sparkle-store-cassandra.key-space" -> "plot"
  )

  lazy val config = modifiedConfig(ConfigFactory.load(), configOverrides: _*)
  ConfigureLogback.configureLogging(config.getConfig("sparkle-time-server"))
  lazy val server = ServerLaunch(config)

}