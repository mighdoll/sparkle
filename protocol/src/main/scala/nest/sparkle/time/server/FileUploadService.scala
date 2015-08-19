package nest.sparkle.time.server

import scala.concurrent.duration._
import com.typesafe.config.Config

import akka.util.Timeout
import akka.actor._
import spray.can.Http
import spray.http._
import HttpMethods._
import spray.can.Http.RegisterChunkHandler
import spray.http.HttpHeaders._

import nest.sparkle.store.WriteableStore

//
// We use a separate port and a lower level interface for now, due to limitations in the
// spray routing layer for chunked uploads. Revisit when this is fixed in akka.http.
//
/** A spray.io service that listens on a tcp port for http upload requests to the /file-upload
  * port.
  *
  * Currently allows cross origin requests from any host, so be sure this port is locked down.
  */
class FileUploadService(rootConfig: Config, store:WriteableStore) extends Actor with ActorLogging {
  implicit val timeout: Timeout = 1.second // for the actor 'asks'

  /** number of rows to read in a block */
  val batchSize = rootConfig.getInt("sparkle.files-loader.batch-size")

  def corsHeaders: List[String] = {
    val headerStrings = corsBaseHeaders.map(_.name)
    (headerStrings ::: corsAdditionalHeaders)
  }

  private lazy val corsBaseHeaders: List[ModeledCompanion] = List(`Content-Type`, Origin, Accept,
    `Accept-Encoding`, `Accept-Language`, Host, `User-Agent`, `Authorization`, `Cache-Control`,
    `Connection`, `Content-Length`)

  private lazy val corsAdditionalHeaders: List[String] = List("Referer", "Pragma", "X-Requested-With", "DNT", "Keep-Alive") // SPRAY why no Referer?

  private lazy val corsHeaderString = corsHeaders.mkString(", ")

  def receive = {
    // when a new connection comes in we register ourselves as the connection handler
    case _: Http.Connected => sender ! Http.Register(self)

    case r@HttpRequest(OPTIONS, Uri.Path("/file-upload"), headers, entity, protocol) =>
      sender ! HttpResponse(
        status = 200,
        headers = List(`Access-Control-Allow-Origin`(AllOrigins), // TODO limit to only this host
                       `Access-Control-Allow-Methods`(GET, POST, OPTIONS),
                       `Access-Control-Allow-Headers`(corsHeaderString)
        ),
        entity = ""
      )

    case request@HttpRequest(POST, Uri.Path("/file-upload"), headers, entity: HttpEntity.NonEmpty, protocol) =>
      // emulate chunked behavior for POST requests to this path
      val parts = request.asPartStream()
      val client = sender
      val handler = context.actorOf(Props(
          new FileUploadHandler(rootConfig, store, batchSize, client,
                                parts.head.asInstanceOf[ChunkedRequestStart])
        ))
      parts.tail.foreach(handler !)

    case request@ChunkedRequestStart(HttpRequest(POST, Uri.Path("/file-upload"), _, _, _)) =>
      val client = sender
      val handler = context.actorOf(Props(new FileUploadHandler(rootConfig, store, batchSize, client, request)))
      sender ! RegisterChunkHandler(handler)

    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")

    case akka.io.Tcp.PeerClosed => // TODO close FileUploadHandler if necessary here

    case x =>
      log.debug(s"unexpected message: $x")
  }
}
