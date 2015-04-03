package nest.sparkle.tools

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Paths, Path }
import java.nio.file.StandardOpenOption.{ CREATE, TRUNCATE_EXISTING, WRITE }
import scala.concurrent.{ ExecutionContext, Future }
import com.typesafe.config.Config
import nest.sparkle.store.Store
import nest.sparkle.util.StringUtil.firstPart
import scala.concurrent.Promise
import scala.util.Success

/** An Exporter that exports data from the Store to a .tsv file */
case class FileExporter(rootConfig: Config, store: Store) // format: OFF
    (implicit val execution: ExecutionContext) extends TsvExporter { // format: ON

  lazy val outputPath: Path = {
    val output = rootConfig.getString("exporter.output")
    val path = Paths.get(output)
    if (Files.notExists(path)) {
      Files.createDirectories(path)
    }
    if (!(Files.isDirectory(path) && Files.isWritable(path))) {
      throw new RuntimeException(s"$output is not a writable directory")
    }
    path
  }

  /** export a leaf dataSet to a .tsv file, where a leaf dataSet is a dataSet
    * with only columns (not other dataSets) as children */
  def exportLeafDataSet(dataSet: String): Future[Unit] = {
    leafDatSet(dataSet).flatMap { tabular =>
      export(dataSet + ".tsv", tabular)
    }
  }

  /** export a column to a .tsv file */
  def exportColumn(columnPath: String): Future[Unit] = {
    column(columnPath).flatMap { tabular =>
      export(firstPart(columnPath) + ".tsv", tabular)
    }
  }

  /** export a Tabular to a .tsv file */
  private def export(fileName: String, tabular: Tabular): Future[Unit] = {
    val done = Promise[Unit]()
    val filePath = outputPath.resolve(fileName)
    log.info(s"exporting file: $filePath")
    Files.createDirectories(filePath.getParent())
    val writer = Files.newBufferedWriter(filePath, UTF_8, CREATE, TRUNCATE_EXISTING, WRITE)
    val lines = tsvLines(tabular)
    lines.doOnError { e =>
      log.error("export failed", e)
      writer.close()
    }.doOnCompleted {
      writer.close()
      done.complete(Success(()))
    }.subscribe { line =>
      writer.write(line)
    }

    done.future
  }

}
