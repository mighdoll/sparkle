package nest.sg

import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}

import akka.actor.ActorSystem

import nest.sparkle.store.{ReadWriteStore, WriteNotification, Store}
import nest.sparkle.time.server.SparkleAPIServer
import nest.sparkle.util.ConfigUtil._
import nest.sparkle.util.{ConfigUtil, LogUtil}
import nest.sparkle.util.RandomUtil.randomAlphaNum
import nest.sparkle.shell.SparkConnection

/** a collection of handy functions useful from the repl console */
object SparkleConsole extends SparkleConsole

/** a collection of handy functions useful from the repl console */
trait SparkleConsole
    extends StorageConsole
    with MeasurementConsole
    with PlotConsole
    with LoaderConsole {

  def configOverrides: Seq[(String, Any)] = Seq(
    s"$sparkleConfigName.port" -> 2323,
    s"$sparkleConfigName.web-root.resource" -> Seq("web/sg/plot-default")
  )

  val rootConfig = modifiedConfig(ConfigFactory.load(), configOverrides: _*)
  ConfigUtil.dumpConfigToFile(rootConfig)
  LogUtil.configureLogging(rootConfig)

  var currentStore:Option[ReadWriteStore] = None
  var currentSpark:Option[SparkConnection] = None
  var currentServer:Option[SparkleAPIServer] = None
  var consoleConfig:Config = rootConfig
  def sparkleConfig = ConfigUtil.configForSparkle(rootConfig)
  implicit lazy val system = ActorSystem("sparkleConsole"+ randomAlphaNum(4), sparkleConfig)
  implicit lazy val executionContext = system.dispatcher


  def store:ReadWriteStore = currentStore.getOrElse {
    currentStore.getOrElse {
      val notification = new WriteNotification()
      val newStore = Store.instantiateReadWriteStore(sparkleConfig, notification)
      currentStore = Some(newStore)
      newStore
    }
  }

  def server:SparkleAPIServer = {
    currentServer.getOrElse {
      val newServer = new SparkleAPIServer(consoleConfig, store)
      currentServer = Some(newServer)
      newServer
    }
  }

  def spark:SparkConnection = {
    currentSpark.getOrElse {
      var newSpark = SparkConnection(consoleConfig)
      currentSpark = Some(newSpark)
      newSpark
    }
  }

  /** use a different cassandra keyspace other than the default */
  def use(keySpace:String): Unit = {
    consoleConfig = ConfigUtil.modifiedConfig(consoleConfig,
      "sparkle.sparkle-store-cassandra.key-space" -> keySpace)
    closeCurrent()
  }

  def closeCurrent(): Unit = {
    currentStore.foreach(_.close())
    currentServer.foreach(_.close())
    currentSpark.foreach(_.close())

    currentStore = None
    currentSpark = None
    currentServer = None
  }

  override def close (): Unit = {
    super.close()
    closeCurrent()
    system.shutdown()
    system.awaitTermination(10.seconds)
  }

}
