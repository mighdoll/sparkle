// TODO: Move this to the tools project.

package nest.sparkle.tools

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config
import spray.util._

import org.clapper.argot.ArgotConverters._

import nest.sparkle.store.Store
import nest.sparkle.store.cassandra.WriteNotification
import nest.sparkle.util.{SparkleApp, Log, ConfigUtil}

/** Main program to run Exporter.
  */
object ExporterMain extends SparkleApp with Log {
  val appName = "sparkle-exporter"
  val appVersion = "0.6.0"

  val dataSet = parser.option[String](List("d", "dataset"), "dataset", "DataSet to export")
  
  initialize()
  
  try {
    FileExporterApp(rootConfig, dataSet.value.get).export()
  } catch {
    case e: Exception => e.printStackTrace()
  } finally {
    sys.exit()
  }

}

/** application that does .tsv file exporting */
case class FileExporterApp(rootConfig: Config, dataSet: String) extends Log {
  import scala.concurrent.ExecutionContext.Implicits.global
  
  val notification = new WriteNotification
  val sparkleConfig = ConfigUtil.configForSparkle(rootConfig)
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