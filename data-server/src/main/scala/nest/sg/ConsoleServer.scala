package nest.sg

import com.typesafe.config.ConfigFactory

import nest.sparkle.time.server.SparkleAPIServer
import nest.sparkle.util.ConfigUtil.{modifiedConfig, sparkleConfigName, configForSparkle}
import nest.sparkle.util.LogUtil

/** launch a sparkle server for a console based debugging app */
trait ConsoleServer {

  def configOverrides: Seq[(String, Any)] = Seq(
    s"$sparkleConfigName.port" -> 2323,
    s"$sparkleConfigName.web-root.resource" -> Seq("web/sg/plot-default")
//    s"$sparkleConfigName.sparkle-store-cassandra.key-space" -> "plot"
  )

  val rootConfig = modifiedConfig(ConfigFactory.load(), configOverrides: _*)
  LogUtil.configureLogging(rootConfig)
  val server = SparkleAPIServer(rootConfig)

}