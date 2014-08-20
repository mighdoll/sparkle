package nest.sg

import com.typesafe.config.ConfigFactory

import nest.sparkle.time.server.SparkleAPIServer
import nest.sparkle.util.ConfigUtil.{modifiedConfig, sparkleConfigName, configForSparkle}
import nest.sparkle.util.LogUtil

/** launch a sparkle server for a console based debugging app */
trait ConsoleServer {

  private def configOverrides: List[(String, Any)] = List(
    s"$sparkleConfigName.port" -> 2323,
    s"$sparkleConfigName.web-root.resource" -> List("web/sg/plot-default")
//    s"$sparkleConfigName.sparkle-store-cassandra.key-space" -> "plot"
  )

  val config = modifiedConfig(ConfigFactory.load(), configOverrides: _*)
  LogUtil.configureLogging(config)
  val server = SparkleAPIServer(config)

}