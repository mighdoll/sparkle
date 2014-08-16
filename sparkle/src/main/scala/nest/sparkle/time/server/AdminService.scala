package nest.sparkle.time.server

import scala.concurrent.duration._
import scala.concurrent.{ Future, ExecutionContext }
import com.typesafe.config.Config
import akka.actor.ActorSystem
import akka.io.IO
import akka.util.Timeout
import akka.actor.{ Props, Actor, ActorRefFactory }
import akka.pattern.ask
import spray.can.Http
import spray.routing.{ Route, HttpService, RequestContext }
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.routing.Directives
import spray.http.StatusCodes.NotFound
import spray.http.MediaTypes.`text/tab-separated-values`
import spray.http.HttpHeaders.{ `Content-Disposition`, `Content-Type` }
import spray.util._
import nest.sparkle.util.Log
import nest.sparkle.store.Store
import nest.sparkle.time.protocol.{ ExportData, HttpLogging }
import nest.sparkle.time.protocol.AdminProtocol._
import nest.sparkle.tools.DownloadExporter
import nest.sparkle.util.StringUtil
import nest.sparkle.store.DataSetNotFound
import scala.util.Failure
import scala.util.Success

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
    val port = rootConfig.getInt("sparkle-time-server.admin.port")
    val interface = rootConfig.getString("sparkle-time-server.admin.interface")

    import system.dispatcher
    implicit val timeout = Timeout(10.seconds)
    val started = IO(Http) ? Http.Bind(serviceActor, interface = interface, port = port)
    started.map { _ => () }
  }

}
