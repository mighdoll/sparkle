/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.util

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import akka.dispatch.Futures

import spray.http.StatusCodes
import spray.http.HttpHeaders._
import spray.http.CacheDirectives._
import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable

import spray.can.Http
import spray.routing.{Directives, HttpServiceActor, Route}
import spray.routing.Directive.pimpApply
import spray.routing.directives.OnCompleteFutureMagnet.apply
import spray.util._

import com.codahale.metrics.MetricFilter
import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}

/**
 * Support sending or exposing metrics.
 */
object MetricsSupport {

  /**
   * Build and start a Graphite reporter.
   * @param graphiteConfig chisel.metrics.graphite config object
   * @return
   */
  def startGraphiteReporter(graphiteConfig: Config): Option[GraphiteReporter] = {
    if (graphiteConfig.getBoolean("collector.enable")) {
      val prefix = graphiteConfig.getString("prefix")
      val host = graphiteConfig.getString("reporter.host")
      val port = graphiteConfig.getInt("reporter.port")
      val interval = graphiteConfig.getDuration("reporter.interval", TimeUnit.MINUTES)
      
      val graphite = new Graphite(new InetSocketAddress(host, port))
      val reporter = GraphiteReporter.forRegistry(MetricsInstrumentation.registry)
        .prefixedWith(prefix)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.SECONDS)
        .filter(MetricFilter.ALL)
        .build(graphite)
      
      reporter.start(interval, TimeUnit.MINUTES)
      
      Some(reporter)
    } else {
      None
    }
  }

  /**
   * Start a spray web server to return metrics when requested.
   * @param graphiteConfig chisel.metrics.graphite config object
   */
  def startCollector(graphiteConfig: Config): Future[Any] = {
    if (graphiteConfig.getBoolean("reporter.enable")) {
      val config = graphiteConfig.getConfig("collector")

      implicit val system = ActorSystem("metrics", config)

      val serviceActor = system.actorOf(Props(new MetricsServer(graphiteConfig)), "metrics-server")

      val port = config.getInt("port")
      val interface = config.getString("interface")

      implicit val timeout = Timeout(10.seconds)
      IO(Http) ? Http.Bind(serviceActor, interface = interface, port = port)
    } else {
      Futures.successful[Any](null)
    }
  }
  
  private class MetricsServer(graphiteConfig: Config)
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

    val notFound: Route = {
      unmatchedPath { path => 
        respondWithHeader(`Cache-Control`(`no-cache`,`max-age`(0))) {
          complete(StatusCodes.NotFound -> "not found")
        }
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
      val writer = new GraphiteWriter(stream)
      val reporter = GraphiteReporter.forRegistry(MetricsInstrumentation.registry)
        //.prefixedWith(prefix)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.SECONDS)
        .build(writer)
      reporter.report()
      
      stream.toString(writer.charset)
    }
  }

  /**
   * Subclass Graphite to write to a stream instead of a socket.
   * @param stream Place to write output.
   */
  class GraphiteWriter(stream: OutputStream) 
    extends Graphite(null) 
  {
    val charset = "UTF-8"
    private var out: BufferedWriter = null
    
    override def connect() {
      if (out != null) throw new IllegalStateException("Already connected")
      out = new BufferedWriter(new OutputStreamWriter(stream, Charset.forName(charset)))
    }
    
    override def close() {
      if (out != null) {
        out.close()
        out = null
      }
    }
    
    override def send(name: String, value: String, timestamp: Long) {
      out.write(sanitize(name))
      out.write(' ')
      out.write(sanitize(value))
      out.write(' ')
      out.write(timestamp.toString)
      out.write('\n')
      out.flush()
    }
  }
}
