package nest.sparkle.tools

import java.io.File
import org.clapper.argot._
import org.clapper.argot.ArgotConverters._
import nest.sparkle.util.{ ArgotApp, Log }
import com.typesafe.config.{ Config, ConfigFactory }
import java.util.concurrent.TimeUnit
import spray.util._
import nest.sparkle.util.ConfigUtil
import nest.sparkle.store.cassandra.WriteNotification
import nest.sparkle.store.Store

/** Main program to run Exporter.
  */
object ExporterMain extends ArgotApp with Log {

  val parser = new ArgotParser("exporter", preUsage = Some("Version 0.1"))
  val help = parser.flag[Boolean](List("h", "help"), "show this help")
  val confFile = parser.option[String](List("conf"), "conf", "path to an application.conf file")
  val dataSet = parser.option[String](List("d", "dataset"), "dataset", "DataSet to export")

  try {
    app(parser, help) {
      val rootConfig = ConfigUtil.configFromFile(confFile.value)
      FileExporterApp(rootConfig, dataSet.value.get).export()
    }
  } catch {
    case e: Exception => e.printStackTrace()
  } finally {
    sys.exit()
  }

}

/** application that does .tsv file exporting */
case class FileExporterApp(rootConfig: Config, dataSet:String) extends Log {
  import scala.concurrent.ExecutionContext.Implicits.global
  
  val notification = new WriteNotification
  val sparkleConfig = rootConfig.getConfig("sparkle-time-server")
  lazy val store = Store.instantiateStore(sparkleConfig, notification)
  val exporter = FileExporter(rootConfig, store)

  /** export a folder in the Store to a .tsv file. The file location is controlled by the .conf file */
  def export() {
    val timeout = rootConfig.getDuration("exporter.timeout", TimeUnit.MILLISECONDS)
    timed("Exporter:") {
      exporter.exportFiles(dataSet).await(timeout)
    }
  }

  /** run a function inside a timer */  // TODO move this to util
  def timed[T](msg: String)(fn: => T): T = {
    val t0 = System.currentTimeMillis()
    try {
      fn
    } finally {
      val t1 = System.currentTimeMillis()
      log.info(s"$msg finished in %d seconds" format (t1 - t0) / 1000L)
    }
  }

}