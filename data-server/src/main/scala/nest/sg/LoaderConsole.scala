package nest.sg

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Promise
import scala.util.Success
import com.typesafe.config.Config
import scala.concurrent.duration._

import akka.actor.ActorSystem

import nest.sparkle.loader.FilesLoader
import nest.sparkle.store.{FileLoaded, DirectoryLoaded, Store, WriteableStore}
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.util.FutureAwait.Implicits._

trait LoaderConsole {
  def writeStore: WriteableStore
  def store: Store
  def rootConfig: Config
  implicit def system: ActorSystem

  val loaders = ArrayBuffer[FilesLoader]()

  private lazy val sparkleConfig = configForSparkle(rootConfig)


  def loadFiles(path:String): Unit = { // LATER clean up on quit
    awaitWrite(path) {
      loaders += new FilesLoader(sparkleConfig, path, path, writeStore, Some(false))
    }
  }

  def watchFiles(path:String): Unit = { // LATER clean up on quit
    awaitWrite(path) {
      loaders += new FilesLoader(sparkleConfig, path, path, writeStore, Some(true))
    }
  }

  def close():Unit = {
    loaders.foreach { loader => loader.close() }
  }

  /** call a loader function and wait for notification that loading has succeeded */
  private def awaitWrite
      ( path:String, waitTime:FiniteDuration = 10.seconds )
      ( loaderFn: => Unit ): Unit = {
    val written = Promise[Unit]()
    def done():Unit = if (!written.isCompleted) written.complete(Success(Unit))

    store.writeListener.listen(path).subscribe {
      _ match {
        case DirectoryLoaded(`path`) => done()
        case FileLoaded(`path`)      => done()
        case _ =>
      }
    }
    loaderFn
    written.future.await(waitTime)
  }

}
