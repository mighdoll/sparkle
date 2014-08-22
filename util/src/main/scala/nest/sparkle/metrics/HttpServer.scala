package nest.sparkle.metrics

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout

import spray.http.StatusCodes
import spray.http.HttpHeaders._
import spray.http.CacheDirectives._
import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable

import spray.can.Http
import spray.routing.{Directives, HttpServiceActor, Route}
import spray.routing.Directive.pimpApply
import spray.routing.directives.OnCompleteFutureMagnet.apply
import spray.util._

import com.codahale.metrics.graphite.{GraphiteReporter => CHGraphiteReporter}

import nest.sparkle.util.{Log, MetricsInstrumentation}

/** HTTP Server with URL to return Metrics in Graphite format.
 * 
 * @param graphiteConfig Graphite metrics configuration object
 */
class HttpServer(graphiteConfig: Config)
  extends HttpServiceActor
  with Directives
  with Log
  //with HttpLogging
{
  val prefix = graphiteConfig.getString("prefix")
  
  def receive: Receive = runRoute(route)

  /**
   * Simple route to test that metrics http server is operational.
   */
  val health: Route = {
    path("health") {
      respondWithHeader(`Cache-Control`(`max-age`(60))) {
        complete("ok")
      }
    }
  }

  /**
   * Route to return Graphite metrics on demand
   */
  val metrics: Route = {
    path("metrics" / "graphite") {
      //respondWithHeader(`Cache-Control`(`max-age`(60))) {
        complete(reportMetrics)
      //}
    }
  }

  /** all api endpoints */
  val route = {  // format: OFF
    get {
      health ~
      metrics 
    }
  } // format: ON
  
  def reportMetrics: String = {
    // FUTURE: Use a chunked response
    val stream = new ByteArrayOutputStream(1024)
    val writer = new Graphite(stream)
    val reporter = CHGraphiteReporter.forRegistry(MetricsInstrumentation.registry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.SECONDS)
      .build(writer)
    reporter.report()
    
    stream.toString(writer.charset)
  }
}

object HttpServer {
  /**
   * Start a spray http server to return metrics when requested.
   * @param graphiteConfig chisel.metrics.graphite config object
   */
  def start(graphiteConfig: Config, system: ActorSystem): Future[Any] = {
    implicit val actorSystem = system
    val config = graphiteConfig.getConfig("collector")

    val serviceActor = system.actorOf(Props(new HttpServer(graphiteConfig)), "metrics-server")

    val port = config.getInt("port")
    val interface = config.getString("interface")

    implicit val timeout = Timeout(10.seconds)
    IO(Http) ? Http.Bind(serviceActor, interface = interface, port = port)
  }
}
