package nest.sparkle.loader

import java.nio.file.{Files, Path}
import com.typesafe.config.Config

import scala.concurrent.{Promise, Future, ExecutionContext}
import scala.util.Success

import nest.sparkle.store.{Store, Event, WriteableStore}
import nest.sparkle.store.cassandra.{RecoverCanSerialize, WriteableColumn}
import nest.sparkle.util.Exceptions._
import nest.sparkle.util.Log
import scala.collection.JavaConverters._

case class FileLoaderTypeConfiguration(msg: String) extends RuntimeException(msg)
case class PathToDataSetFailed(msg: String) extends RuntimeException(msg)

/** A facility for loading files into a data store */
class SingleFileLoader
    ( sparkleConfig: Config, store: WriteableStore, batchSize: Int )
    ( implicit executionContext: ExecutionContext)
    extends Log {

  val recoverCanSerialize = new RecoverCanSerialize(sparkleConfig)

  /** Load a single file into the store. Returns a future that completes
    * when loading is complete.
    * @param fullPath - filesystem path of the file to load (e.g. /tmp/myFiles/foo.csv)
    * @param pathName - notify listeners that this name has been loaded
    * @param dataSetRoot - filesystem root for computing the columnPath of the loaded data (e.g. /tmp )
    */
  def loadFile
      ( fullPath: Path, pathName:String, dataSetRoot:Path )
      : Future[Unit] = {
    val complete = Promise[Unit]()
    fullPath match {
      case ParseableFile(format) if Files.isRegularFile(fullPath) =>
        log.info(s"Started loading $fullPath into the Store")
        val relativePath = dataSetRoot.relativize(fullPath)
        TabularFile.load(fullPath, format).map { rowInfo =>
          val dataSet = pathToDataSet(relativePath)
          log.info(s"loading rows from $relativePath into dataSet: $dataSet")
          val loaded =
            loadRows(rowInfo, dataSet).andThen {
              case _ => rowInfo.close()
            }

          loaded.foreach { _ =>
            log.info(s"file loaded: $fullPath")
            complete.complete(Success(Unit))
            store.writeNotifier.fileLoaded(pathName)
          }
          loaded.failed.foreach { failure => log.error(s"loading $fullPath failed", failure) }
        }
      case x => log.warn(s"$fullPath could not be parsed, ignoring")
        complete.complete(Success(Unit))
    }

    complete.future
  }


  /** Store data rows into columns the Store, return a future that completes with
    * the name of the dataset into which the columns were written
    */
  private def loadRows
      ( rowInfo: CloseableRowInfo, dataSet: String )
      : Future[String] = {
    /** collect up paths and futures that complete with column write interface objects */
    val pathAndColumns: Seq[(String, Future[WriteableColumn[Any, Any]])] = {
      rowInfo.valueColumns.map {
        case StringColumnInfo(name, _, parser) =>
          val columnPath = dataSet + "/" + name
          val serializeValue = recoverCanSerialize.tryCanSerialize[Any](parser.typed).getOrElse {
            log.error(s"can't file serializer for type ${parser.typed}")
            throw FileLoaderTypeConfiguration(s"can't find canSerialize for type: ${parser.typed}")
          }
          import nest.sparkle.store.cassandra.serializers.LongSerializer
          val column = store.writeableColumn(columnPath)(LongSerializer, serializeValue)
          val anyColumn = column map { futureColumn =>
            futureColumn.asInstanceOf[WriteableColumn[Any, Any]]
          }
          (columnPath, anyColumn)
      }
    }

    val (columnPaths, futureColumns) = pathAndColumns.unzip

    /** write all the row data into storage columns. The columns in the provided Seq
      * should match the order of the rowInfo data columns.
      */
    def writeColumns(rowInfo: RowInfo,
      columns: Seq[WriteableColumn[Any, Any]]): Future[Unit] = {
      rowInfo.keyColumn || NYI("tables without key column")

      def rowToEvents(row: RowData): Seq[Event[Any, Any]] = {
        for {
          key <- row.key(rowInfo).toSeq
          valueOpt <- row.values(rowInfo)
          value <- valueOpt
        } yield {
          Event(key, value)
        }
      }

      val rowGroups = rowInfo.rows.grouped(batchSize)
      val eventsInColumnsBlocks =
        for {
          group <- rowGroups
          _ = log.info(s"loading batch of ${group.length} rows")
          eventsInRows = group.map { row => rowToEvents(row) }
        } yield {
          eventsInRows.transpose
        }

      val allWrites =
        for {
          eventsColumns <- eventsInColumnsBlocks
          (events, column) <- eventsColumns.zip(columns)
        } yield {
          column.write(events)
        }

      Future.sequence(allWrites).map { _ => () }
    }

    val pathWritten: Future[String] =
      for {
        columns <- Future.sequence(futureColumns)
        written <- writeColumns(rowInfo, columns)
      } yield {
        dataSet
      }

    pathWritten
  }


  /** Make the DataSet string from the file's path.
    *
    * The dataset string is the file's path minus any parts beginning with an
    * underscore.
    *
    * Skips path elements beginning with an underscore.
    * Strips off .tsv or .csv suffixes
    *
    * After stripping and skipping _ prefixed files, if no path components
    * remain for the dataset, use the store's default dataset.
    *
    * @param path Path of the tsv/csv file
    * @return The DataSet as a string.
    */
  private def pathToDataSet(path: Path): String = {
    val parentOpt: Option[String] = Option(path.getParent).map { parent =>
      val parentElements =
        parent.iterator.asScala.filterNot(_.toString.startsWith("_"))
      parentElements.mkString("/")
    }

    val fileName = path.getFileName.toString

    val combined =
      if (fileName.startsWith("_")) {
        parentOpt match {
          case Some(parent) => parent
          case None         => Store.defaultDataSet
        }
      } else {
        val strippedFileName = fileName.stripSuffix(".tsv").stripSuffix(".csv")
        parentOpt match {
          case Some(parent) => s"$parent/$strippedFileName"
          case None         => strippedFileName
        }
      }

    combined
  }

}
