/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.loader

import nest.sparkle.util.WatchPath
import nest.sparkle.util.WatchPath._
import akka.actor.ActorSystem
import java.nio.file.Path
import java.nio.file.Paths
import nest.sparkle.store.WriteableStore
import java.nio.file.Files
import nest.sparkle.store.cassandra.serializers._
import scala.concurrent.Future
import nest.sparkle.store.cassandra.WriteableColumn
import scala.collection.immutable.Range
import nest.sparkle.store.Event
import nest.sparkle.util.Exceptions.NYI
import scala.concurrent.Promise
import scala.util.Success
import org.slf4j.LoggerFactory

/** emitted to the event stream when the file has been completely loaded */
case class LoadComplete(filePath: String)

object FilesLoader {
  def apply(rootDirectory: String, store: WriteableStore) // format: OFF
      (implicit system: ActorSystem): FilesLoader = { // format: ON
    new FilesLoader(rootDirectory, store)
  }
}

/** load all the events in the csv/tsv files in a directory. */
class FilesLoader(rootDirectory: String, store: WriteableStore)(implicit system: ActorSystem) {
  val log = LoggerFactory.getLogger(classOf[FilesLoader])
  implicit val executor = system.dispatcher
  val root = Paths.get(rootDirectory)

  if (Files.isDirectory(root)) {
    val watcher = WatchPath(root)
    val initialFiles = watcher.watch{ change => fileChange(change, store) }
    initialFiles.foreach{ futureFiles =>
      futureFiles.foreach{ path =>
        loadFile(root.resolve(path), store)
      }
    }
  } else {
    loadFile(root, store)
  }

  /** called when a file is changed in the directory we're watching */
  private def fileChange(change: Change, store: WriteableStore) {
    change match {
      case Added(path) =>
        loadFile(path, store)
      case Removed(path) =>
        log.warn(s"removed $path.  ignoring for now")
      case Modified(path) =>
        log.warn(s"modified $path.  ignoring for now")
    }
  }

  private def loadFile(fullPath: Path, store: WriteableStore) {
    fullPath match {
      case ParseableFile(format) if Files.isRegularFile(fullPath) =>
        TabularFile.load(fullPath, format).map { rowInfo =>
          loadRows(rowInfo, store, fullPath).andThen {
            case _ => rowInfo.close()
          } foreach { _ =>
            system.eventStream.publish(LoadComplete(fullPath.toString))
          }
        }
      case x => // ignore non-parseable files
    }
  }

  private def loadRows(rowInfo: CloseableRowInfo, store: WriteableStore, path: Path): Future[Path] = {
    val finished = Promise[Path]
    val pathString = path.toString

    /** indices of RowData columns that we'll store (i.e. not the time column) */
    val valueColumnIndices = {
      val indices = Range(0, rowInfo.names.size)
      rowInfo.keyColumn match {
        case None           => indices
        case Some(rowIndex) => indices.filterNot(_ == rowIndex)
      }
    }

    val futureColumnsWithIndex: Seq[Future[(Int, WriteableColumn[Long, Double])]] =
      valueColumnIndices.map { index =>
        val name = rowInfo.names(index)
        val columnPath = pathString + "/" + name
        store.writeableColumn[Long, Double](columnPath) map { futureColumn =>
          (index, futureColumn)
        }
      }

    /** create the columns in Storage, in case they don't exist already */
    def createColumns[T, U](columns: Seq[WriteableColumn[T, U]]): Future[Unit] = {
      val created =
        columns.map { column =>
          column.create(s"loaded from file: $pathString")
        }
      Future.sequence(created).map { _ => () }
    }

    def writeColumns[T, U](rowInfo: RowInfo, columnsWithIndex: Seq[(Int, WriteableColumn[T, U])]) {
      rowInfo.keyColumn.isDefined || NYI("tables without key column")

      rowInfo.rows.foreach { row =>
        for {
          (index, column) <- columnsWithIndex
          value <- row.values(index)
          key <- row.key(rowInfo)
        } {
          val event = Event(key.asInstanceOf[T], value.asInstanceOf[U])
          column.write(Seq(event))
        }
      }

    }

    for {
      columnsWithIndex <- Future.sequence(futureColumnsWithIndex)
      columns = columnsWithIndex map { case (index, column) => column }
      created <- createColumns(columns)
    } {
      writeColumns(rowInfo, columnsWithIndex)
      finished.complete(Success(path))
    }

    finished.future
  }

}

