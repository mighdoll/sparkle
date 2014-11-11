package nest.sparkle.loader.kafka

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import com.typesafe.config.Config

import akka.actor.{ActorSystem, Props}

import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing._
import spray.util._

import nest.sparkle.http.{ResourceLocation, AdminServiceActor, AdminService}
import nest.sparkle.util.kafka.Utils
import nest.sparkle.util.kafka._
import nest.sparkle.util.kafka.KafkaJsonProtocol._

/**
 * Kafka Loader Admin Service.
 *
 * Contains the specific routes for the Kafka loader's admin pages.
 */
trait KafkaLoaderAdminService extends AdminService
{
  override lazy val webRoot = Some(ResourceLocation("web/admin/loader"))
  
  implicit lazy val zkProps = {
    val connectTimeout = sparkleConfig.getInt("kafka-loader.kafka-reader.zookeeper.connection.timeout.ms")
    val sessionTimeout = sparkleConfig.getInt("kafka-loader.kafka-reader.zookeeper.session.timeout.ms")

    ZkConnectProps(
      sparkleConfig.getString("kafka-loader.kafka-reader.zookeeper.connect"),
      connectTimeout millis,
      sessionTimeout millis
    )
  }
  
  lazy val groupPrefix = sparkleConfig.getString("kafka-loader.reader.consumer-group-prefix")
  lazy val topicNames = sparkleConfig.getStringList("kafka-loader.topics").asScala.toSeq
   
  /** Returns an array of JSON objects for each topic being loaded */
  lazy val allTopics: Route = {
    get {
      path("topics") {
        onComplete(Utils.getTopics) {
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
        onComplete(Utils.getBrokers) {
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
        onComplete(getConsumerGroups) {
          case Success(s)   => complete(s)
          case Failure(x)   => complete(StatusCodes.InternalServerError -> x.toString)
        }
      }
    }
  }
   
  /** Returns a list of topic/partitions offsets for the specified consumer group */
  lazy val offsets: Route = {
    get {
      path("offsets") {
        //dynamic {
          onComplete(getOffsets) {
            case Success(s)   => complete(s)
            case Failure(x)   => complete(StatusCodes.InternalServerError -> x.toString)
          }
        //}
      }
    }
  }

  /** Return a future for the list of consumer groups matching the prefix.
    * 
    * FUTURE: Using caching for the return value.
    * 
    * @return Future
    */
  private def getConsumerGroups: Future[Seq[String]] = {
    Utils.getConsumerGroups.map { groups => 
      groups.filter(_.startsWith(groupPrefix))
    }
  }
  
  private def getOffsets: Future[Seq[KafkaGroupOffsets]] = {
    def processGroups(groups: Seq[String]): Future[Seq[KafkaGroupOffsets]] = {
      val futures = groups map { group => 
        Utils.getGroupOffsets(group)
      }
      Future.sequence(futures)
    }
    
    for {
      groups <- getConsumerGroups
      groupOffsets <- processGroups(groups)
    } yield {
      groupOffsets
    }
  }

  override lazy val routes: Route = {
    allTopics ~
    brokers ~
    consumerGroups ~
    offsets
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
