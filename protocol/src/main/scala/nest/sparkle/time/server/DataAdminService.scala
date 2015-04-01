package nest.sparkle.time.server

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.config.Config

import spray.http.StatusCodes

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout

import spray.can.Http
import spray.http.HttpHeaders.`Content-Disposition`
import spray.routing.Directive.pimpApply
import spray.routing.Route

import nest.sparkle.http.{BaseAdminService, BaseAdminServiceActor}
import nest.sparkle.store.{ReadWriteStore, Store}
import nest.sparkle.tools.DownloadExporter
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.measure.Measurements
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport._

/** a web api for serving an administrative page about data stored in sparkle: download .tsv files, etc. */
trait DataAdminService extends BaseAdminService with DataService {
  implicit def actorSystem: ActorSystem
  def store: Store
  implicit def executionContext: ExecutionContext


  override lazy val indexHtml: Route =
    redirect("/admin/index.html", StatusCodes.MovedPermanently)

  lazy val exporter = DownloadExporter(rootConfig, store)(actorSystem.dispatcher)

  lazy val fetch: Route =
    get {
      extract(_.request) { request =>
        path("fetch" / Segments) { segments =>
          val futureResult = fetchHelper(segments.mkString("/"))
          val fileName = segments.mkString("_") + ".tsv"
          respondWithHeader(`Content-Disposition`("attachment", Map(("filename", fileName)))) {
            richComplete(futureResult)
          }
        }
      }
    }

  override lazy val routes: Route = {// format: OFF
      fetch ~
      v1protocol ~
      get {
        jsServiceConfig
      }
  } // format: ON

  /** Fetch the given input. Treat the input as a column path, if that fails,
    * treat the input as a leaf dataSet */
  private def fetchHelper(input: String): Future[TsvContent] = {
    exporter.exportColumn(input).map { TsvContent(_) }.recoverWith {
      case t => exporter.exportLeafDataSet(input).map { TsvContent(_) }
    }
  }

}

/** an AdminService inside an actor (the trait can be used for testing */
class ConcreteBaseDataAdminService(val actorSystem: ActorSystem, val store: Store, rootConfig: Config, measurements: Measurements)
  extends BaseAdminServiceActor(actorSystem, measurements, rootConfig)
  with DataAdminService

/** start an admin service */
object DataAdminService {
  def start(rootConfig: Config, store: ReadWriteStore, measurements: Measurements)
    (implicit actorSystem: ActorSystem): Future[Unit] = {
    val serviceActor = actorSystem.actorOf(
      Props(new ConcreteBaseDataAdminService(actorSystem, store, rootConfig, measurements)),
      "admin-server"
    )
    val port = configForSparkle(rootConfig).getInt("admin.port")
    val interface = configForSparkle(rootConfig).getString("admin.interface")

    import actorSystem.dispatcher
    implicit val timeout = Timeout(10.seconds)
    val started = IO(Http) ? Http.Bind(serviceActor, interface = interface, port = port)


    val uploadActor = actorSystem.actorOf(
      Props(new FileUploadService(rootConfig, store)), "upload-service"
    )
    val uploadPort = configForSparkle(rootConfig).getInt("admin.upload-port")

    val startedUpload = IO(Http) ? Http.Bind(uploadActor, interface = interface, port = uploadPort)

    for {
      _ <- started
      _ <- startedUpload
    } yield ()
  }

}
