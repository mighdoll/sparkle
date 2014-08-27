package nest.sparkle.metrics

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config
import akka.actor.{Actor, ActorRefFactory, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http

/**
 * Object that is loaded to implement MetricsHttpServer.
 * 
 * LogUtil only references the trait MetricsHttpServer, not this object
 * which is loaded by reflection. This allows metrics-graphite to be
 * an optional dependency; it is only required on the classpath if the
 * MetricHttpServer is enabled in the conf file.
 */
object HttpServer
  extends MetricsHttpServer
{
  /**
   * Start a spray http server to return metrics when requested.
   * @param graphiteConfig chisel.metrics.graphite config object
   */
  def start(graphiteConfig: Config, system: ActorSystem): Future[Any] = {
    val config = graphiteConfig.getConfig("http")
    val port = config.getInt("port")
    val interface = config.getString("interface")


    val serviceActor = system.actorOf(Props(new MetricsServiceActor), "metrics-server")
    implicit val actorSystem = system
    implicit val timeout = Timeout(10.seconds)
    IO(Http) ? Http.Bind(serviceActor, interface = interface, port = port)
  }
}

/**
 * Actor for the Metrics HTTP Service
 */
class MetricsServiceActor
  extends Actor
  with MetricsService
{
  override def actorRefFactory: ActorRefFactory = context
  
  def receive: Receive = runRoute(routes)
}
