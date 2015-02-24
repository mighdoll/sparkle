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

package nest.sparkle.metrics

import java.io.Closeable

import scala.concurrent.Future

import com.typesafe.config.Config
import akka.actor.ActorSystem

import nest.sparkle.util.Instance

/**
 * Support sending or exposing metrics.
 */
object MetricsSupport {

  /**
   * Build and start a Graphite reporter.
   */
  def startGraphiteReporter(graphiteConfig: Config): Option[Closeable] = {
    if (graphiteConfig.getBoolean("reporter.enable")) {
      val obj = Instance.objectByClassName[MetricsGraphiteReporter]("nest.sparkle.metrics.GraphiteReporter")
      val reporter = obj.start(graphiteConfig)
      Some(reporter)
    } else {
      None
    }
  }

  /**
   * Start a spray http server to return metrics when requested.
   */
  def startHttpServer(graphiteConfig: Config)(implicit system: ActorSystem): Future[Any] = {
    if (graphiteConfig.getBoolean("http.enable")) {
      val obj = Instance.objectByClassName[MetricsHttpServer]("nest.sparkle.metrics.HttpServer")
      obj.start(graphiteConfig, system)
    } else {
      Future.successful[Any](null)
    }
  }
  
}

trait MetricsGraphiteReporter {
  def start(graphiteConfig: Config): Closeable
}

trait MetricsHttpServer {
  def start(graphiteConfig: Config, system: ActorSystem): Future[Any]
}
