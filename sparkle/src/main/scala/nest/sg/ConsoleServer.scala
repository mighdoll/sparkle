package nest.sg

import nest.sparkle.time.server.ServerLaunch

/** launch a sparkle server for a console based debugging app */
trait ConsoleServer {

  private def configOverrides: List[(String, Any)] = List(
    "sparkle-time-server.port" -> 2323,
    "sparkle-time-server.web-root.resource" -> List("web/sg/plot-default")
//    "sparkle-time-server.sparkle-store-cassandra.key-space" -> "plot"
  )

  lazy val server = ServerLaunch(None, configOverrides: _*)

}