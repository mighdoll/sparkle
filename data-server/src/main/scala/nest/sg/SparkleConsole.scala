package nest.sg

import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}

import akka.actor.ActorSystem

import nest.sparkle.store.{ReadWriteStore, WriteNotification, Store}
import nest.sparkle.time.server.{SparkleAPIServer, ConfigurationError}
import nest.sparkle.util.ConfigUtil._
import nest.sparkle.util.{ConfigUtil, LogUtil, InitializeReflection}
import nest.sparkle.util.RandomUtil.randomAlphaNum

/** a collection of handy functions useful from the repl console */
object SparkleConsole extends SparkleConsole

/** a collection of handy functions useful from the repl console */
trait SparkleConsole
    extends StorageConsole
    with MeasurementConsole
    with PlotConsole
    with LoaderConsole {

  InitializeReflection.init

  def configOverrides: Seq[(String, Any)] = Seq(
    s"$sparkleConfigName.port" -> 2323,
    s"$sparkleConfigName.web-root.resource" -> Seq("web/sg/plot-default")
  )

  val rootConfig = modifiedConfig(ConfigFactory.load(), configOverrides: _*)
  LogUtil.configureLogging(rootConfig)

  val sparkleConfig = ConfigUtil.configForSparkle(rootConfig)
  implicit lazy val system = ActorSystem("sparkleConsole"+ randomAlphaNum(4), sparkleConfig)
  implicit lazy val executionContext = system.dispatcher

  val notification = new WriteNotification()
  var currentStore:Option[ReadWriteStore] = None

  connectStore()
  lazy val server = new SparkleAPIServer(rootConfig, store)

  def store:ReadWriteStore = currentStore.getOrElse {
    throw ConfigurationError("store not initialized")
  }

  /** use a different cassandra keyspace other than the default */
  def use(keySpace:String): Unit = {
    connectStore(keySpace)
  }

  protected def connectStore(): Unit = {
    connectStoreWithConfig(rootConfig)
  }

  protected def connectStore(keySpace:String): Unit = {
    val modifiedConfig = ConfigUtil.modifiedConfig(rootConfig,
      "sparkle.sparkle-store-cassandra.key-space" -> keySpace)
    connectStoreWithConfig(modifiedConfig)
  }

  private def connectStoreWithConfig(theRootConfig:Config): Unit = {
    val theSparkleConfig = ConfigUtil.configForSparkle(theRootConfig)
    val newStore = Store.instantiateReadWriteStore(theSparkleConfig, notification)
    currentStore.foreach(_.close())
    currentStore = Some(newStore)
  }

  override def close (): Unit = {
    super.close()
    currentStore.foreach(_.close())
    server.close()
    system.shutdown()
    system.awaitTermination(10.seconds)
  }

}
