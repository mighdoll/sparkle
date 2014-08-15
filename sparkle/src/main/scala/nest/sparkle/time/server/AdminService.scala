package nest.sparkle.time.server

import scala.concurrent.duration._
import com.typesafe.config.Config
import akka.actor.ActorSystem
import akka.io.IO
import akka.util.Timeout
import akka.actor.Props
import akka.actor.Actor
import akka.pattern.ask
import akka.actor.ActorRefFactory
import spray.can.Http
import spray.routing.{ Route, HttpService }
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.routing.Directives
import spray.util._
import nest.sparkle.util.Log
import nest.sparkle.store.Store
import nest.sparkle.time.protocol.{ ExportData, HttpLogging }
import nest.sparkle.time.protocol.AdminProtocol._
import nest.sparkle.tools.DownloadExporter
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

// TODO DRY with DataService
// TODO use 
/** a web api for serving an administrative page about data stored in sparkle: downlaod .tsv files, etc. */  
trait AdminService extends HttpService with Directives with HttpLogging with Log {
  implicit def system: ActorSystem
  def rootConfig: Config
  def store: Store
  implicit def executionContext: ExecutionContext

  lazy val exporter = DownloadExporter(rootConfig, store)(system.dispatcher)

  lazy val staticBuiltIn = { // static data from web html/javascript files pre-bundled in the 'web' resource path
    getFromResourceDirectory("web/admin")
  }

  lazy val indexHtml: Route = { // return index.html from custom folder if provided, otherwise use built in default page
    pathSingleSlash {
      getFromResource("web/admin/index.html")
    }
  }

  lazy val fetch: Route =
    path("fetch") {
      post {
        // TODO parse Accept type: must be blank or text/tab-seprated-values
        entity(as[ExportData]) { export =>
          val futureResult = exporter.exportFolder(export.folder)
          // TODO set content-disposition header
          complete(futureResult)
        }
      }
    }

  val route: Route = {// format: OFF
    withRequestResponseLog {
      get {
        indexHtml ~
        staticBuiltIn 
      } ~
      post {
        fetch
      }
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

    import system.dispatcher
    implicit val timeout = Timeout(10.seconds)
    val started = IO(Http) ? Http.Bind(serviceActor, interface = "0.0.0.0", port = 9876)  // TODO make port configurable
    started.map{_ => ()}
  }

}
