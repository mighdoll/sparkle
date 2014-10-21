package nest.sparkle.http

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.config.Config

import akka.actor.{Actor, ActorRefFactory, ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout

import spray.can.Http
import spray.routing.Directive.pimpApply
import spray.routing.Route

import nest.sparkle.util.ConfigUtil.configForSparkle

/** a web api for serving an administrative page */
trait AdminService 
  extends StaticContent 
          with HttpLogging 
{
  implicit def executionContext: ExecutionContext
  def rootConfig: Config

  override lazy val webRoot = Some(ResourceLocation("web/admin"))
  
  /** Subclasses override this with specific admin routes */
  lazy val routes: Route = staticContent

  lazy val route: Route = {// format: OFF
    withRequestResponseLog {
      routes ~
      staticContent
    }    
  } // format: ON

}

/** an AdminService inside an actor (the trait can be used for testing */
class AdminServiceActor(val system: ActorSystem, val rootConfig: Config) 
  extends Actor 
          with AdminService 
{
  override def actorRefFactory: ActorRefFactory = context
  def receive: Receive = runRoute(route)
  def executionContext = context.dispatcher
}

/** start an admin service */
object AdminService {
  def start(actor: AdminServiceActor): Future[Unit] = {
    implicit val system = actor.system
    val serviceActor = system.actorOf(Props(actor),"admin-server")
    
    val sparkleConfig = configForSparkle(actor.rootConfig)
    val port = sparkleConfig.getInt("admin.port")
    val interface = sparkleConfig.getString("admin.interface")

    import system.dispatcher
    implicit val timeout = Timeout(10.seconds)
    val started = IO(Http) ? Http.Bind(serviceActor, interface = interface, port = port)
    started.map { _ => () }
  }
}
