package nest.sparkle.loader.kafka

import scala.annotation.tailrec
import scala.reflect.runtime.universe._
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.control.Exception.nonFatalCatch

import org.clapper.argot.ArgotConverters._

import nest.sparkle.store.{WriteableStore, Store}
import nest.sparkle.store.WriteNotification  
import nest.sparkle.util.{Log, InitializeReflection, SparkleApp}
import nest.sparkle.util.ConfigUtil.sparkleConfigName

/** main() entry point to launch a kafka loader.  Pass a command line argument (--conf) to
  * point to the config file to use.  The server will launch with default configuration otherwise.
  */
object Main 
  extends SparkleApp 
          with Log 
{
  override def appName = "kafka-loader"
  override def appVersion = "0.6.0"
  
  val erase = parser.flag[Boolean](List("format"), "erase and format the database")
  
  initialize()
  InitializeReflection.init
  
  lazy val notification = new WriteNotification()
  
  /** Time to wait between attempts to create store */
  private val storeRetryWait = 1.minute.toMillis
  
  val loader = {
    val writeableStore = createStoreWithRetry
    val storeKeyType = typeTag[Long]
    new KafkaLoader(rootConfig, writeableStore)(storeKeyType, system)
  }
  
  loader.start()
  log.info("Kafka loaders started")
  
  @volatile
  var keepRunning = true
  
  // handle SIGTERM which upstart uses to terminate a service
  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      loader.shutdown()
      keepRunning = false
    }
  })
  
  KafkaLoaderAdminService.start(system, measurements, rootConfig)
  
  while (keepRunning) {
    Thread.sleep(5.seconds.toMillis)
    log.trace("alive") // FUTURE: write status info to the log or some other housekeeping
  }
  
  system.shutdown()
  
  log.info("Main loader thread terminating")
  
  override def overrides = {
    erase.value.map { (s"$sparkleConfigName.erase-store", _) }.toSeq
  }

  /**
   * Create a writeable store. On failure keeps retrying until success.
   * @return writeable store
   */
  @tailrec
  private def createStoreWithRetry: WriteableStore = {
    val tryStore = nonFatalCatch withTry Store.instantiateWritableStore(sparkleConfig, notification)
    tryStore match {
      case Success(store) => store
      case Failure(err)   =>
        log.error("store create failed", err)
        Thread.sleep(storeRetryWait)
        createStoreWithRetry
    }
    
  }
}
