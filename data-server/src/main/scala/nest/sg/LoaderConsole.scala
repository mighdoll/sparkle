package nest.sg

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Promise
import scala.util.Success
import com.typesafe.config.Config
import scala.concurrent.duration._

import akka.actor.ActorSystem

import nest.sparkle.loader.FilesLoader
import nest.sparkle.store.{DirectoryLoaded, Store, WriteableStore}
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.util.FutureAwait.Implicits._

trait LoaderConsole {
  def writeStore: WriteableStore
  def store: Store
  def rootConfig: Config
  implicit def system: ActorSystem

  val loaders = ArrayBuffer[FilesLoader]()

  private lazy val sparkleConfig = configForSparkle(rootConfig)

  private def awaitWrite(path:String)(fn: =>Unit):Unit = {
    val written = Promise[Unit]()
    store.writeListener.listen(path).subscribe {
        _ match {
        case DirectoryLoaded(`path`) => written.complete(Success(Unit))
        case _ =>
      }
    }
    fn
    written.future.await(45.seconds)
  }

  def loadFiles(path:String): Unit = { // LATER clean up on quit
    awaitWrite(path) {
      loaders += new FilesLoader(sparkleConfig, path, path, writeStore, 0, Some(false))
    }
  }

  def watchFiles(path:String): Unit = { // LATER clean up on quit
    awaitWrite(path) {
      loaders += new FilesLoader(sparkleConfig, path, path, writeStore, 0, Some(true))
    }
  }

  def close():Unit = {
    loaders.foreach { loader => loader.close() }
  }

}
