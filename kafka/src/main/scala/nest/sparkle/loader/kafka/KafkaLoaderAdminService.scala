package nest.sparkle.loader.kafka

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import com.typesafe.config.Config

import akka.actor.{ActorSystem, Props}

import spray.routing._

import nest.sparkle.http.{ResourceLocation, AdminServiceActor, AdminService}

/**
 * Kafka Loader Admin Service.
 *
 * Contains the specific routes for the Kafka loader's admin pages.
 */
trait KafkaLoaderAdminService extends AdminService
{
  override lazy val webRoot = Some(ResourceLocation("web/admin/loader"))

  lazy val temp: Route =
    get {
      path("temp") {
        complete("temp")
      }
    }
  
  override lazy val routes: Route = {
    temp
  }
}

class KafkaLoaderAdminServiceActor(system: ActorSystem, rootConfig: Config) 
  extends AdminServiceActor(system, rootConfig)
  with KafkaLoaderAdminService
{
}

object KafkaLoaderAdminService {
  def start(rootConfig: Config)(implicit system: ActorSystem): Future[Unit] = {
    val serviceActor = system.actorOf(Props(new KafkaLoaderAdminServiceActor(system, rootConfig)),"admin-server")
    AdminService.start(rootConfig, serviceActor)
  }
}
