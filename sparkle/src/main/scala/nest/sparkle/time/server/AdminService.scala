package nest.sparkle.time.server

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config

import akka.actor.{Actor, ActorRefFactory, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import nest.sparkle.store.Store
import nest.sparkle.time.protocol.HttpLogging
import nest.sparkle.tools.DownloadExporter
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.util.Log
import spray.can.Http
import spray.http.HttpHeaders.{ `Content-Disposition` }
import spray.routing.{Directives, Route}
import spray.routing.Directive.pimpApply

// TODO DRY with DataService
/** a web api for serving an administrative page about data stored in sparkle: downlaod .tsv files, etc. */
trait AdminService extends StaticContent with Directives with RichComplete with HttpLogging with Log {
  implicit def system: ActorSystem
  def rootConfig: Config
  def store: Store
  implicit def executionContext: ExecutionContext

  lazy val exporter = DownloadExporter(rootConfig, store)(system.dispatcher)

  override lazy val webRoot = Some(ResourceLocation("web/admin"))

  lazy val fetch: Route =
    get {
      extract(_.request) { request =>
        path("fetch" / Segments) { segments =>
          val folder = segments.mkString("/")
          val futureResult = exporter.exportFolder(folder).map { TsvContent(_) }
          val fileName = segments.last + ".tsv"
          respondWithHeader(`Content-Disposition`("attachment", Map(("filename", fileName)))) {
            richComplete(futureResult)
          }
        }
      }
    }

  val route: Route = {// format: OFF
    withRequestResponseLog {
      staticContent ~
      fetch
    }    
  } // format: ON

}

/** an AdminService inside an actor (the trait can be used for testing */
class ConcreteAdminService(val system: ActorSystem, val store: Store, val rootConfig: Config) extends Actor with AdminService {
  override def actorRefFactory: ActorRefFactory = context
  //  override def actorSystem = context.system
  def receive: Receive = runRoute(route)
  def executionContext = context.dispatcher

}

/** start an admin service */
object AdminService {
  def start(rootConfig: Config, store: Store)(implicit system: ActorSystem): Future[Unit] = {
    val serviceActor = system.actorOf(
      Props(new ConcreteAdminService(system, store, rootConfig)),
      "admin-server"
    )
    val port = configForSparkle(rootConfig).getInt("admin.port")
    val interface = configForSparkle(rootConfig).getString("admin.interface")

    import system.dispatcher
    implicit val timeout = Timeout(10.seconds)
    val started = IO(Http) ? Http.Bind(serviceActor, interface = interface, port = port)
    started.map { _ => () }
  }

}
