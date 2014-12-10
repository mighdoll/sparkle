package nest.sparkle.loader.kafka

import java.util.concurrent.Executors

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import com.typesafe.config.Config

import akka.actor.{ActorSystem, Props}

import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.routing._

import nest.sparkle.http.{ResourceLocation, AdminServiceActor, AdminService}
import nest.sparkle.measure._
import nest.sparkle.util.kafka.KafkaStatus
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

  /** A different ExecutionContext is used than normal for the KafkaStatus calls since they run
    * synchronous zookeeper and kafka commands which will tie up a thread for a long time. 
    */
  implicit val kafkaStatusContext: ExecutionContext
  
  lazy val groupPrefix = sparkleConfig.getString("kafka-loader.reader.consumer-group-prefix")
  lazy val topicNames = sparkleConfig.getStringList("kafka-loader.topics").asScala.toSeq
   
  /** Returns an array of JSON objects for each topic being loaded */
  lazy val allTopics: Route = {
    get {
      path("topics") {
        implicit val span = Span.startNoParent("admin.allTopics", level = Trace)
        timedOnComplete(KafkaStatus.allTopics, "/topics")
      }
    }
  }
   
  /** Returns an array of JSON objects for each topic being loaded */
  lazy val allTopics2: Route = {
    get {
      path("topics2") {
        implicit val span = Span.startNoParent("admin.allTopics2", level = Trace)
        timedOnComplete(KafkaStatus.allTopics2, "/topics2")
      }
    }
  }
    
  /** Info on a single topic */
  lazy val oneTopic: Route = {
    get {
      path("topics" / Segment) { topicName =>
        implicit val span = Span.startNoParent("admin.oneTopic", level = Trace)
        timedOnComplete(KafkaStatus.topicFromName(topicName), s"/topics/$topicName")
      }
    }
  }
 
  /** Returns an array of JSON objects for each Kafka broker */
  lazy val brokers: Route = {
    get {
      path("brokers") {
        implicit val span = Span.startNoParent("admin.brokers", level = Trace)
        timedOnComplete(KafkaStatus.allBrokers, "/brokers")
      }
    }
  }
   
  /** Returns the list of consumer groups matching the prefix */
  lazy val groups: Route = {
    get {
      path("groups") {
        implicit val span = Span.startNoParent("admin.consumerGroups", level = Trace)
        timedOnComplete(consumerGroups(), "/groups")
      }
    }
  }
   
  /** Returns a list of topic/partitions offsets for the specified consumer group */
  lazy val offsets: Route = {
    get {
      path("offsets") {
        implicit val span = Span.startNoParent("admin.offsets", level = Trace)
        timedOnComplete(groupOffsets(), "/offsets")
      }
    }
  }

  /** Return a future for the list of consumer groups matching the prefix.
    * 
    * FUTURE: Using caching for the return value.
    * 
    * @return Future
    */
  private def consumerGroups()(implicit parentSpan: Span): Future[Seq[String]] = {
    KafkaStatus.allConsumerGroups.map { groups => 
      groups.filter(_.startsWith(groupPrefix))
    }
  }
  
  private def groupOffsets()(implicit parentSpan: Span): Future[Seq[KafkaGroupOffsets]] = {
    def processGroups(groups: Seq[String]): Future[Seq[KafkaGroupOffsets]] = {
      val futures = groups map { group => 
        KafkaStatus.consumerGroupOffsets(group)
      }
      Future.sequence(futures)
    }
    
    for {
      groups       <- consumerGroups()
      groupOffsets <- processGroups(groups)
    } yield {
      groupOffsets
    }
  }

  override lazy val routes: Route = {
    allTopics ~ allTopics2 ~
    oneTopic ~
    brokers ~
    groups ~
    offsets
  }
}

class KafkaLoaderAdminServiceActor(system: ActorSystem, measurements: Measurements, rootConfig: Config)
  extends AdminServiceActor(system, measurements, rootConfig)
  with KafkaLoaderAdminService
{
  private lazy val statusThreadPool = Executors.newCachedThreadPool()
  lazy val kafkaStatusContext = ExecutionContext.fromExecutor(statusThreadPool)
}

object KafkaLoaderAdminService {
  def start(system: ActorSystem, measurements: Measurements, rootConfig: Config): Future[Unit] = {
    val serviceActor = system.actorOf(Props(new KafkaLoaderAdminServiceActor(system, measurements, rootConfig)),"admin-server")
    AdminService.start(rootConfig, serviceActor)(system)
  }
}
