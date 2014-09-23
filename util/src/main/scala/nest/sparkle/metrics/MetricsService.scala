package nest.sparkle.metrics

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

import spray.http.StatusCodes
import spray.http.HttpHeaders._
import spray.http.CacheDirectives._
import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable

import spray.routing.{HttpService, Route}
import spray.routing.Directive.pimpApply
import spray.routing.directives.OnCompleteFutureMagnet.apply
import spray.util._

import com.codahale.metrics.graphite.{GraphiteReporter => CHGraphiteReporter}

import nest.sparkle.util.{Log, Instrumented, MetricsInstrumentation}

/** HTTP Server with URL to return Metrics in Graphite format.
 */
trait MetricsService
  extends HttpService
  with Instrumented
  with Log
  //with HttpLogging
{
  /**
   * Simple route to test that metrics http server is operational.
   */
  val healthRoute: Route = {
    path("health") {
      respondWithHeader(`Cache-Control`(`max-age`(60))) {
        complete("ok")
      }
    }
  }

  /**
   * Route to return Graphite metrics on demand
   */
  val metricsRoute: Route = {
    path("metrics" / "graphite") {
      //respondWithHeader(`Cache-Control`(`max-age`(60))) {
        complete(reportMetrics)
      //}
    }
  }

  /** all api endpoints */
  val routes = {  // format: OFF
    get {
      healthRoute ~
      metricsRoute 
    }
  } // format: ON
  
  def reportMetrics: String = {
    // FUTURE: Use a chunked response
    val stream = new ByteArrayOutputStream(1024)
    val writer = new Graphite(stream)
    val reporter = CHGraphiteReporter.forRegistry(MetricsInstrumentation.registry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(writer)
    reporter.report()
    
    stream.toString(writer.charset)
  }
}
