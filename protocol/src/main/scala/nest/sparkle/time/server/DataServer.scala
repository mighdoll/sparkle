package nest.sparkle.time.server

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import com.typesafe.config.Config
import spray.can.Http

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout

import nest.sparkle.http.{FileLocation, FileOrResourceLocation, ResourceLocation}
import nest.sparkle.measure.Measurements
import nest.sparkle.store.Store
import nest.sparkle.util.FutureAwait.Implicits._
import nest.sparkle.util.{ConfigUtil, Log}

case class ConfigurationError(msg: String) extends RuntimeException
/** An actor serving data DataRegistry data via a spray based REST api.  The
  * server is configured with user provided extensions extracted from the config file.
  */
class ConfiguredDataServer // format: OFF
    ( val store: Store, val rootConfig: Config )
    ( implicit val measurements:Measurements )
    extends Actor with ConfiguredDataService { // format: ON
  override def actorRefFactory: ActorRefFactory = context
  override def actorSystem = context.system
  def receive: Receive = runRoute(route)
  def executionContext = context.dispatcher

  override lazy val webRoot: Option[FileOrResourceLocation] = configuredWebRoot()
  
  /** Return the configured web-root from the configured settings for web-root.directory and
   *  web-root.resource */
  private def configuredWebRoot():Option[FileOrResourceLocation] = {
    def oneConfiguredString(configPath: String): Option[String] = {
      val list = ConfigUtil.configForSparkle(rootConfig).getStringList(configPath).asScala
      if (list.size > 1) {
        throw ConfigurationError(s"web-root contains more than one setting for $configPath:  $list")
      }
      list.headOption
    }

    val directory = oneConfiguredString("web-root.directory") map FileLocation
    val resource = oneConfiguredString("web-root.resource") map ResourceLocation

    (directory, resource) match {
      case (Some(_), Some(_)) =>
        throw ConfigurationError(s"web-root directory _and_ resource defined. Just define one. (directory: $directory, resource: $resource)")
      case (directory@Some(_), None) => directory
      case (None, file@Some(_))      => file
      case (None, None)              => None
    }
  }

}

trait Closeable {
  def close(): Unit
}

object ConfiguredDataServer extends Log {

  def startServer
      ( rootConfig:Config, store:Store, name:String = "sparkle-server" )
      ( implicit system:ActorSystem, measurements:Measurements )
      : Closeable = {
    val sparkleConfig = ConfigUtil.configForSparkle(rootConfig)

    val serviceActor = system.actorOf(Props(
      new ConfiguredDataServer(store, rootConfig)),
      name
    )
    lazy val webPort = sparkleConfig.getInt("port")

    startServer(rootConfig, serviceActor, webPort, store)
  }

  /** Launch the http server for sparkle API requests.
    *
    * This call will block until the server is ready to accept incoming requests.
    */
  private def startServer( rootConfig:Config, serviceActor: ActorRef, port: Int, store:Store ) // format: OFF
                 ( implicit system: ActorSystem, measurements: Measurements ): Closeable = { // format: ON
    log.info("---- starting server ----")
    implicit val timeout = Timeout(10.seconds)
    val started = IO(Http) ? Http.Bind(serviceActor, interface = "0.0.0.0", port = port)
    started.await // wait until server is started

    val webSocket = new DataWebSocket(store, rootConfig)

    new Closeable {
      override def close(): Unit = webSocket.shutdown()
    }
  }

}
