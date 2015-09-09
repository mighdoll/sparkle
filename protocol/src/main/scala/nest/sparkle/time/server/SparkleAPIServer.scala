package nest.sparkle.time.server

import java.awt.Desktop
import java.net.URI

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

import com.typesafe.config.Config
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.util._

import nest.sparkle.loader.{ FilesLoader, LoadPathDoesNotExist }
import nest.sparkle.store.{ReadWriteStore, Store, WriteNotification}
import nest.sparkle.util._
import nest.sparkle.measure.ConfiguredMeasurements

/** A server that supports the sparkle api over both http and websocket,
  * over separate ports, and an admin web service on a third port.
  * Configuration comes from the .conf file settings via rootConfig
  * Data is served from the provided readWriteStore.
  */
class SparkleAPIServer // format: OFF
    ( rootConfig: Config, val readWriteStore: ReadWriteStore )
    ( implicit val system: ActorSystem ) extends Log { // format: ON
  import system.dispatcher
  val sparkleConfig = ConfigUtil.configForSparkle(rootConfig)
  def store = readWriteStore
  lazy val webPort = sparkleConfig.getInt("port")
  implicit val measurements = new ConfiguredMeasurements(rootConfig)
  var openWebSocket: Option[DataWebSocket] = None

  def actorSystem = system

  val service = system.actorOf(
      props = Props(new ConfiguredDataServer(store, rootConfig)),
      name = "sparkle-server"
    )
  
  DataAdminService.start(rootConfig, store, measurements).await(10.seconds)

  /* Note that nothing may be specified to be auto-start but we started an
   * actor system so the main process will not end. This will disappear when
   * each component gets their own Main/SparkleAPIServer.
   */
  possiblyErase()
  possiblyStartFilesLoader()
  startServer(service, webPort)
  
  /** (for desktop use) Open the web browser to the sparkle http server.
    */
  def launchDesktopBrowser(path: String = ""): Unit = {
    val uri = new URI(s"http://localhost:$webPort/$path")
    println(s"browsing to: $uri")
    import system.dispatcher
    RepeatingRequest.get(uri + "health").onComplete {
      case Success(_) =>
        val desktop = Desktop.getDesktop
        desktop.browse(uri)
      case Failure(err) =>
        Console.err.println(s"failed to launch server: ${err.getMessage}")
        sys.exit(1)
    }
  }

  def close(): Unit = {
    openWebSocket.foreach { dataWebSocket =>
      dataWebSocket.shutdown()
    }
    measurements.close()
  }
  
  /** Launch the http server for sparkle API requests.
    *
    * This call will block until the server is ready to accept incoming requests.
    */
  private def startServer(serviceActor: ActorRef, port: Int)  {
    if (sparkleConfig.getBoolean("auto-start")) {
      log.info("---- starting server ----")
      implicit val timeout = Timeout(10.seconds)
      val started = IO(Http) ? Http.Bind(serviceActor, interface = "0.0.0.0", port = port)
      started.await // wait until server is started
  
      val webSocket = new DataWebSocket(store, rootConfig)
      openWebSocket = Some(webSocket)
    }
  }

  /** Erase and reformat the storage system if requested */
  private def possiblyErase(): Unit = {
    if (sparkleConfig.getBoolean("erase-store")) {
      readWriteStore.format()
    }
  }

  /** launch a FilesLoader for each configured directory */
  private def possiblyStartFilesLoader(): Unit = {
    val loaderConfig = sparkleConfig.getConfig("files-loader")
    if (loaderConfig.getBoolean("auto-start")) {
      loaderConfig.getStringList("directories").asScala.foreach { pathString =>
        val watch = loaderConfig.getBoolean("watch-directories")
        try {
          new FilesLoader(sparkleConfig, pathString, pathString, readWriteStore, Some(watch))
        } catch {
          case LoadPathDoesNotExist(path) => sys.exit(1)
        }
      }
    }
  }
}

object SparkleAPIServer extends Log {
  /** convenience wrapper for creating a SparkleAPIServer object from command line arguments
    */
  def apply(rootConfig: Config): SparkleAPIServer = {

    val sparkleConfig = ConfigUtil.configForSparkle(rootConfig)
    implicit val system = ActorSystem("sparkle", sparkleConfig)

    val notification = new WriteNotification()
    val store = Store.instantiateReadWriteStore(sparkleConfig, notification)

    new SparkleAPIServer(rootConfig, store)
  }
}
