package nest.sparkle.time.server

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.config.Config

import akka.actor.{Actor, ActorRefFactory, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout

import spray.can.Http
import spray.http.HttpHeaders.`Content-Disposition`
import spray.routing.Directive.pimpApply
import spray.routing.{Directives, Route}

import nest.sparkle.http.{ResourceLocation, AdminServiceActor, AdminService => HttpAdminService}
import nest.sparkle.store.Store
import nest.sparkle.tools.DownloadExporter
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.util.Log
import nest.sparkle.measure.Measurements

// TODO DRY with DataService
/** a web api for serving an administrative page about data stored in sparkle: downlaod .tsv files, etc. */
trait AdminService extends HttpAdminService with RichComplete {
  implicit def system: ActorSystem
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

  override lazy val routes: Route = {// format: OFF
      fetch
  } // format: ON

}

/** an AdminService inside an actor (the trait can be used for testing */
class ConcreteAdminService(system: ActorSystem, val store: Store, rootConfig: Config, measurements: Measurements) 
  extends AdminServiceActor(system, measurements, rootConfig) 
  with AdminService 
{
}

/** start an admin service */
object AdminService {
  def start(rootConfig: Config, store: Store, measurements: Measurements)
    (implicit system: ActorSystem): Future[Unit] = 
  {
    val serviceActor = system.actorOf(
      Props(new ConcreteAdminService(system, store, rootConfig, measurements)),
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
