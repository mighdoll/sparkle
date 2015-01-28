package nest.sg

import com.typesafe.config.Config

import akka.actor.ActorSystem

import nest.sparkle.loader.FilesLoader
import nest.sparkle.store.WriteableStore
import nest.sparkle.util.ConfigUtil.configForSparkle

trait LoaderConsole {
  def writeStore: WriteableStore
  def rootConfig: Config
  implicit def system: ActorSystem

  private lazy val sparkleConfig = configForSparkle(rootConfig)

  def loadFiles(path:String): Unit = { // LATER clean up on quit
    new FilesLoader(sparkleConfig, path, path, writeStore, 0, Some(false))
  }

  def watchFiles(path:String): Unit = { // LATER clean up on quit
    new FilesLoader(sparkleConfig, path, path, writeStore, 0, Some(true))
  }

}
