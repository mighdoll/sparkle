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

import java.nio.file._
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.actor.TypedActor
import akka.actor.TypedProps
import WatchPath._
import scala.collection.mutable
import FileSystemScan.scanFileSystem
import akka.actor.ActorRefFactory

trait PathWatcher {
  /** Return the current set of files in the WatchPath.  Report any future additions,
    * deletions, or modifications to matching files in the WatchPath.  
    * Does not currently support symbolic links */
  def watch(fn: WatchPath.Change => Unit): Future[Iterable[Path]]
}

object WatchPath {
  /** Create a typed actor to watch a portion of the filesystem subtree starting at the root path.
    * Clients should call watch() on the returned PathWatcher to capture the initial set of files and
    * start watching for subsequent changes.
    * File reporting is filtered with shell globbing syntax.  
    * (See http://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String))
    */
  def apply(root: Path, glob: String = "**")(implicit system: ActorSystem): PathWatcher = {
    val actorProxy = TypedActor(system).typedActorOf(TypedProps(classOf[PathWatcher],
      new PathWatcherActor(root, glob)), "FileRegistry" + root.getName(root.getNameCount - 1))

    actorProxy
  }

  /** Return matching files in a filesystem subtree (in the root directory or in any nested subdirectory) */
  def scan(root: Path, glob: String): Iterable[Path] = {
    val (files, _) = scanFileSystem(root, glob)
    files
  }

  /** reported to the registered watch function */
  sealed abstract class Change {
    def path:Path
  }
  case class Added(path: Path) extends Change
  case class Removed(path: Path) extends Change
  case class Modified(path: Path) extends Change
}

