package nest.sparkle.tools

import java.io.File
import java.nio.file.{ Paths, Files }
import java.nio.file.StandardOpenOption._
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import org.clapper.argot._
import org.clapper.argot.ArgotConverters._
import com.typesafe.config.{ Config, ConfigFactory }
import spray.util._
import rx.lang.scala.Observable
import nest.sparkle.store.{ Store, DataSet, Column }
import nest.sparkle.util.{ ArgotApp, Log }
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.store.cassandra.WriteNotification

/** Main program to run Exporter.
  */
object ExporterMain extends ArgotApp with Log {

  val parser = new ArgotParser("exporter", preUsage = Some("Version 0.1"))
  val help = parser.flag[Boolean](List("h", "help"), "show this help")
  val confFile = parser.option[String](List("conf"), "conf", "path to an application.conf file")
  val dataSet = parser.option[String](List("d", "dataset"), "dataset", "DataSet to export")

  try {
    app(parser, help) {

      val config = {
        val base = ConfigFactory.load()
        confFile.value.map {
          path =>
            val file = new File(path)
            ConfigFactory.parseFile(file).resolve().withFallback(base)
        } getOrElse {
          base
        }
      }
      val timeout = config.getDuration("exporter.timeout", TimeUnit.MILLISECONDS)
      val t0 = System.currentTimeMillis()
      Exporter(config).processDataSet(dataSet.value.getOrElse("")).await(timeout)
      val t1 = System.currentTimeMillis()
      log.info("Exporter finished in %d seconds" format (t1 - t0) / 1000L)
    }
  } catch {
    case e: Exception => e.printStackTrace()
  } finally {
    sys.exit()
  }

}

/** Tool to export a store to CSV files.
  */
case class Exporter(config: Config)
    extends Log {
  implicit val executor = ExecutionContext.global

  val notification = new WriteNotification
  lazy val store = Store.instantiateStore(config.getConfig("sparkle-time-server"), notification)
  lazy val outputPath = {
    val output = config.getString("exporter.output")
    val path = Paths.get(output)
    if (Files.notExists(path)) {
      Files.createDirectories(path)
    }
    if (!(Files.isDirectory(path) && Files.isWritable(path))) {
      throw new RuntimeException(s"$output is not a writable directory")
    }
    path
  }

  /** Write the columns in this dataSet and child dataSets.
    *
    * @param dataSetString DataSet name to process
    * @return Future complete when columns are exported.
    */
  def processDataSet(dataSetString: String): Future[Unit] = {
    val dataSet = store.dataSet(dataSetString).await(20.seconds)
    processDataSet(dataSet)
  }

  def close(): Unit = {
    store.close()
  }

  /** Write the columns in this dataSet and child dataSets.
    *
    * @param dataSet DataSet to process
    * @return Future when all columns are exported.
    */
  private def processDataSet(dataSet: DataSet): Future[Unit] = {
    log.debug(s"processing ${dataSet.name}")
    val columns = dataSet.childColumns.map {
      columnPath => exportColumn(columnPath)
    }
    val children = dataSet.childDataSets.map {
      child => processDataSet(child)
    }

    (columns ++ children).toFutureSeq.flatMap {
      seq => Future.sequence(seq)
    } map { _ => () }
  }

  private def exportColumn(columnPath: String): Future[Unit] = {
    val future = store.column[Long, Double](columnPath) flatMap { column =>
      {
        writeColumn(columnPath, column)
      }
    }
    future
  }

  /** Write a column to a tsv file.
    *
    * The file is written to the outputPath directory. The dataSet parts are
    * converted into sub-directories. The file name is the column name prefixed
    * by "_' which the FilesLoader will ignore when creating the columnPath
    * when loading the file.
    *
    * @param columnPath
    * @param column
    * @return
    */
  private def writeColumn(columnPath: String, column: Column[Long, Double]): Future[Unit] = {
    val (dataSetName, columnName) = Store.setAndColumn(columnPath)
    val dataSetPath = outputPath.resolve(dataSetName)
    Files.createDirectories(dataSetPath)
    val filePath = dataSetPath.resolve("_" + columnName + ".tsv")
    val writer = Files.newBufferedWriter(
      filePath, StandardCharsets.UTF_8,
      CREATE, TRUNCATE_EXISTING, WRITE
    )
    writer.write(s"time\t$columnName\n")

    val promise = Promise[Unit]()

    val rows = column.readRange().initial.doOnEach { event =>
      val line = s"${event.argument}\t${event.value}\n"
      writer.write(line, 0, line.length)
    } finallyDo { () =>
      writer.close()
      promise.success()
    }
    rows.subscribe()

    promise.future
  }

}
