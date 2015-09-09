/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.util

import scala.collection.mutable
import akka.actor.TypedActor
import java.nio.file._
import scala.concurrent.ExecutionContext
import scala.concurrent.{ Future, Promise }
import java.nio.file.StandardWatchEventKinds._
import com.sun.nio.file.SensitivityWatchEventModifier

import FileSystemScan.scanFileSystem
import scala.collection.JavaConverters._
import WatchPath._
import java.nio.file._

/** Actor that watches a directory subtree using nio.
  * call watch() to start the filesystem watching.
  * Changes are reported to a function provided to watch().
  */
protected[util] class PathWatcherActor(root: Path, glob: String) extends PathWatcher with TypedActor.PostStop {
  implicit val dispatcher = TypedActor.dispatcher

  // call-registered functions to notify on file changes
  private val watchers = mutable.ArrayBuffer[WatchPath.Change => Unit]()

  // file watching actor, created on demand at the first watch() call
  private var fsWatcher: Option[FileSystemWatch] = None

  /** Register a callback on changes, start the filesystem change scanner if necessary,
    * and report the current set of matching files.
    */
  def watch(fn: WatchPath.Change => Unit): Future[Iterable[Path]] = {
    watchers.append(fn)
    fsWatcher = fsWatcher orElse {
      val paths =
        if (Files.isDirectory(root)) {
          val (_, dirs) = scanFileSystem(root, "")
          dirs
        } else {
          Seq(root)
        }
      Some(new FileSystemWatch(change _, glob, paths: _*))
    }

    val (files, _) = scanFileSystem(root, glob)
    Promise.successful(files).future
  }

  /** called internally when the fileSystem watcher notices a change */
  protected def change(change: WatchPath.Change): Unit = {
    watchers.foreach { watcher => watcher(change) }
  }

  def postStop(): Unit = {
    fsWatcher.foreach { _.cancel() }
  }
}

/** Use nio to watch a set of directories for changes to contained files and directories.   Report on
  * any added, removed, or modified files.  If any directories are added to the watched set, watch the
  * added directories for changes too.  */
protected[util] class FileSystemWatch(report: WatchPath.Change => Unit, glob: String, paths: Path*)(implicit context: ExecutionContext) {
  private val watcher = FileSystems.getDefault().newWatchService()
  private val watchKeys = mutable.Map[WatchKey, Path]()
  private val pathMatcher = FileSystems.getDefault.getPathMatcher("glob:" + glob)

  paths foreach watchPath

  /** watch an additional path */
  private def watchPath(path: Path): Unit = {
    val watchKind:Array[WatchEvent.Kind[_]] = Array(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
    val key = path.register(watcher, watchKind, SensitivityWatchEventModifier.HIGH)
    watchKeys += (key -> path)
  }

  private def watchable(path: Path): Boolean = {
    // we've been notified, but the newly created file might not be readable yet
    // so we can't call e.g. Files.isRegular(path)
    (Files.isReadable(path) && !Files.isDirectory(path) && pathMatcher.matches(path))
  }

  @volatile var done = false

  private val runner = new Runnable {
    def run(): Unit = {
      while (!done) {
        val key = watcher.take()
        val events = collectEvents(key)
        processEvents(watchKeys(key), events)
        key.reset()
      }
      watchKeys.keys foreach { _.cancel() }
      watcher.close()
    }

    /** drain all events from the given watch key, calling pollEvents until it returns an empty list */
    def collectEvents(key:WatchKey):Iterable[WatchEvent[_]] = {
      val events = key.pollEvents().asScala
      if (!events.isEmpty) {
        events ++ collectEvents(key)
      } else {
        events
      }
    }

    /** notify caller and internal state after an observed file system event */
    def processEvents(watchedPath:Path, events: Iterable[WatchEvent[_]]): Unit = {
      events.foreach { event =>
        event.kind match {
          case OVERFLOW => ??? // probably should rescan the directories
          case _ =>
            val pathEvent = event.asInstanceOf[WatchEvent[Path]]
            val localPath = pathEvent.context
            val completePath = watchedPath.resolve(localPath)
            pathEvent.kind match {
              case ENTRY_CREATE =>
                if (Files.isDirectory(completePath)) {
                  watchPath(completePath)
                  val (files, dirs) = scanFileSystem(completePath, glob)
                  dirs foreach watchPath
                  files.foreach {file =>
                    val fromRoot = completePath.resolve(file)
                    report(Added(fromRoot))
                  }
                }
                if (watchable(completePath)) {
                  report(Added(completePath))
                }
              case ENTRY_DELETE =>
                if (pathMatcher.matches(completePath)) {
                  report(Removed(completePath))
                }
              case ENTRY_MODIFY =>
                if (watchable(completePath)) {
                  report(Modified(completePath))
                }
            }
        }
      }

    }

  }

  context.execute(runner)

  def cancel(): Unit = {
    done = true
  }
}

