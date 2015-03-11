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

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.Future
import com.typesafe.config.Config

import akka.actor.ActorSystem

import nest.sparkle.store.WriteableStore
import nest.sparkle.util.FileSystemScan._
import nest.sparkle.util.{Log, PathWatcher, WatchPath}
import nest.sparkle.util.WatchPath._

case class LoadPathDoesNotExist(path: String) extends RuntimeException

/** Load all the events in the csv/tsv files in a directory or load a single file.
  * The directory or file must exist.
  *
  * @param loadPath Path to directory to load from/watch or a single file to load.
  * @param pathName name of the file/directory to load (for reporting when loading is complete)
  * @param store Store to write data to.
  * creating the DataSet name.
  */
class FilesLoader // format: OFF
    ( sparkleConfig: Config, loadPath: String, pathName:String,
      store: WriteableStore, watch:Option[Boolean] = None)
    (implicit system: ActorSystem) extends Log { // format: ON

  /** number of rows to read in a block */
  val batchSize = sparkleConfig.getInt("files-loader.batch-size")

  implicit val executor = system.dispatcher
  private val root: Path = Paths.get(loadPath)
  private var closed = false
  private var watcher: Option[PathWatcher] = None
  val singleLoader = new SingleFileLoader(store, batchSize)

  if (Files.notExists(root)) {
    log.error(s"$loadPath does not exist. Can not load data from this path")
    throw LoadPathDoesNotExist(loadPath)
  }

  /** root to use in calculating the dataset. If we're loading a directory,
    * the dataset begins with the subdirectory or file below the directory.
    * If we're loading a single file, the dataset begins with the filename.
    */
  private val dataSetRoot: Path =
    if (Files.isDirectory(root)) {
      root
    } else {
      root.getParent()
    }

  val doWatch = {
    watch match {
      case Some(true) if Files.isDirectory(root) => true
      case Some(true) =>
        log.error(s"Watch requested on $root, which is not a directory")
        false
      case _ => false
    }
  }

  if (doWatch) {
    log.info(s"Watching $loadPath for files to load into store")
    val pathWatcher = WatchPath(root)
    watcher = Some(pathWatcher)
    val initialFiles = pathWatcher.watch{ change => fileChange(change, store) }
    loadFiles(initialFiles)
  } else {
    if (Files.isDirectory(root)) {
      val initialFiles = scanForFiles(root)
      loadFiles(Future.successful(initialFiles))
    } else {
      singleLoader.loadFile(root, pathName, dataSetRoot)
    }
  }

  def loadFiles(futureFiles:Future[Iterable[Path]]): Unit = {
    futureFiles.foreach {files=>
      val loadedSeq =
        for {
          path <- files
          if !closed
        } yield {
          singleLoader.loadFile(root.resolve(path), pathName, dataSetRoot)
        }

      Future.sequence(loadedSeq).foreach { _ =>
        log.info(s"directory loaded $root")
        store.writeNotifier.directoryLoaded(root.toString)
      }
    }
  }

  /** terminate the file loader */
  def close() {
    watcher.foreach { _.close() }
    closed = true
  }

  /** called when a file is changed in the directory we're watching */
  private def fileChange(change: Change, store: WriteableStore): Unit = {
    change match {
      case Added(path) =>
        log.info(s"file added: $path")
        singleLoader.loadFile(path, pathName, dataSetRoot)
      case Removed(path) =>
        log.info(s"file removed: $path (ignoring)")
      case Modified(path) =>
        log.info(s"file modified: $path")
        singleLoader.loadFile(path, pathName, dataSetRoot)
    }
  }


}
