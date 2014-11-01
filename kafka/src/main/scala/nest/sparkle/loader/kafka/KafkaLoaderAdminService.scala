package nest.sparkle.loader.kafka

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.util.{Failure, Success}

import com.typesafe.config.Config

import akka.actor.{ActorSystem, Props}

import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing._

import nest.sparkle.http.{ResourceLocation, AdminServiceActor, AdminService}
import nest.sparkle.util.kafka.Utils
import nest.sparkle.util.kafka.KafkaJsonProtocol._

/**
 * Kafka Loader Admin Service.
 *
 * Contains the specific routes for the Kafka loader's admin pages.
 */
trait KafkaLoaderAdminService extends AdminService
{
  override lazy val webRoot = Some(ResourceLocation("web/admin/loader"))
  
  lazy val zkConnect = sparkleConfig.getString("kafka-loader.kafka-reader.zookeeper.connect")
  lazy val zkSessionTimeout = sparkleConfig.getInt("kafka-loader.kafka-reader.zookeeper.session.timeout.ms")
  lazy val zkConnectionTimeout = sparkleConfig.getInt("kafka-loader.kafka-reader.zookeeper.connection.timeout.ms")
  
  lazy val groupPrefix = sparkleConfig.getString("kafka-loader.reader.consumer-group-prefix")
  lazy val topics = sparkleConfig.getStringList("kafka-loader.topics").asScala.toSeq
   
  /** Returns an array of JSON objects for each topic being loaded */
  lazy val allTopics: Route = {
    get {
      path("topics") {
        val zkutils = Utils(zkConnect)
        onComplete(zkutils.getTopics) {
          case Success(s)   => complete(s)
          case Failure(x)   => complete(StatusCodes.InternalServerError -> x.toString)
        }
      }
    }
  }
  
  /** Returns an array of JSON objects for each Kafka broker */
  lazy val brokers: Route = {
    get {
      path("brokers") {
        val zkutils = Utils(zkConnect)
        onComplete(zkutils.getBrokers) {
          case Success(s)   => complete(s)
          case Failure(x)   => complete(StatusCodes.InternalServerError -> x.toString)
        }
      }
    }
  }
   
  /** Returns the list of consumer groups matching the prefix */
  lazy val consumerGroups: Route = {
    get {
      path("groups") {
        val zkutils = Utils(zkConnect)
        val futureGroups = zkutils.getConsumerGroups.map { groups => 
          groups.filter(_.startsWith(groupPrefix))
        }
        onComplete(futureGroups) {
          case Success(s)   => complete(s)
          case Failure(x)   => complete(StatusCodes.InternalServerError -> x.toString)
        }
      }
    }
  }

  override lazy val routes: Route = {
    allTopics ~
    brokers ~
    consumerGroups
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
