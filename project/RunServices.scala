import sbt._
import sbt.Keys._

import java.io.{InputStreamReader, BufferedReader}
import BackgroundServiceKeys.{start, stop}

object RunServices {
  /** stop a service by using it's admin shutdown port */
  def stopService(streams: TaskStreams, serverName: String, adminPort: Int) {
    val shutdownUrl = new URL("http://localhost:%d/shutdown.txt" format adminPort)
    // trigger that url
    try {
      streams.log.info("stopping: " + serverName + " by GETting " + shutdownUrl)
      val is = shutdownUrl.openStream()
      val reader = new BufferedReader(new InputStreamReader(is))
      var line = reader.readLine()
      while (line != null) {
        streams.log.info("stopping: " + serverName + " - " + line)
        line = reader.readLine()
      }
    } catch {
      case _: java.net.ConnectException => streams.log.info("Service %s not stopped. (perhaps it was never started)".format(serverName))
    }
  }

}
