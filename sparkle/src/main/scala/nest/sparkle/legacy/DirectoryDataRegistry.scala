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

package nest.sparkle.legacy

import java.nio.file.Path
import nest.sparkle.util.WatchPath
import akka.actor.ActorSystem
import collection.mutable
import spray.caching.LruCache
import scala.concurrent.Future
import akka.actor.TypedActor
import spray.util._
import akka.actor.TypedProps
import scala.concurrent.duration._
import scala.util.control.Exception._
import scala.util.Try
import nest.sparkle.util.TryToFuture._
import akka.util.Timeout.durationToTimeout
import nest.sparkle.util.Opt._
import nest.sparkle.util.WatchPath


/** Create a data registry backed by a filesystem subdirectory */
object DirectoryDataRegistry {
  def apply(path:Path, glob:String = "**")(implicit system:ActorSystem):DirectoryDataRegistryApi ={
    TypedActor(system).typedActorOf(
        TypedProps(classOf[DirectoryDataRegistryApi],
                   new DirectoryDataRegistryActor(path, glob)).withTimeout(30.seconds),
        "DirectoryDataRegistry_" + path.toString.replace('/', '-')
      )
  }
}

/** Internal API for the DirectoryDataRegistryActor proxy.  Includes both the public and protected
 *  proxied actor messages.  */
protected trait DirectoryDataRegistryApi extends DataRegistry {
  protected[legacy] def fileChange(change:WatchPath.Change): Unit
}

/** A data registry backed by a filesystem subdirectory */
class DirectoryDataRegistryActor(path:Path, glob:String = "**")
    extends DirectoryDataRegistryApi with TypedActor.PreStart {
  implicit val system = TypedActor.context.system
  implicit val execution = TypedActor.context.dispatcher

  private val self = TypedActor.self[DirectoryDataRegistryApi]
  private val files = mutable.HashSet[String]()
  private val defaultMaxCapacity = 100
  private val loadedSets = LruCache[DataSetOld](maxCapacity = defaultMaxCapacity)
  private val watcher = WatchPath(path, glob)

  def preStart(): Unit = {
    val initialFiles = watcher.watch(self.fileChange)
    val fileNames = initialFiles.await.map(_.toString)
    files ++= fileNames
  }

  /** return the DataSet for the given path string */
  def findDataSet(name:String):Future[DataSetOld] = {
    loadedSets(name, () => loadSet(name))
  }

  def allSets():Future[Iterable[String]] = {
    Future.successful(files)
  }

  /** asynchronously load a .csv or .tsv file */
  private def loadSet(name:String):Future[DataSetOld] = {
    val resolved = path.resolve(name)
    val fullPathTry = Try(resolved.toRealPath())
    fullPathTry.toFuture.flatMap {fullPath =>
      FileLoadedDataSet.loadAsync(fullPath, name)
    }
  }

  /** called when the filesystem watcher notices a change */
  protected[legacy] def fileChange(change:WatchPath.Change): Unit = {
    import WatchPath._

    def localPath(fullPath:Path):String = {
      path.relativize(fullPath).toString
    }

    change match {
      case Added(fullPath) =>
        files += localPath(fullPath)
      case Modified(fullPath) =>
        loadedSets.remove(localPath(fullPath))
      case Removed(fullPath) =>
        val changedPath = localPath(fullPath)
        loadedSets.remove(changedPath)
        files.remove(changedPath)
    }
  }

  def postStop(): Unit = {
    TypedActor(system).stop(watcher)
  }

}
