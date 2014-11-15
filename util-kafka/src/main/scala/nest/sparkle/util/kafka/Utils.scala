package nest.sparkle.util.kafka

import java.util.concurrent.{Executors, SynchronousQueue, TimeUnit, ThreadPoolExecutor}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future, future}
import scala.concurrent.duration._

import spray.json._

import org.I0Itec.zkclient.ZkClient
import org.apache.zookeeper.data.Stat

import kafka.common.TopicAndPartition
import kafka.consumer.SimpleConsumer
import kafka.api.{OffsetRequest, Request}
import kafka.utils.{ZkUtils, ZKStringSerializer}

import nest.sparkle.util.Log

import KafkaJsonProtocol._

/**
 * Functions to get Kafka information
 * 
 * @param connectString Zookeeper connect string, e.g. "localhost:2181"
 * @param connectionTimeout Zookeeper connection timeout
 * @param sessionTimeout Zookeeper session timeout
 * @param executionContext Thread pool to use for synchronous Zookeeper requests.
 */
class Utils(
  val connectString: String, 
  val connectionTimeout: FiniteDuration = 30.seconds,
  val sessionTimeout: FiniteDuration = 30.seconds
  )(implicit executionContext: ExecutionContext)
  extends Log
{
  val client = new ZkClient(
    connectString, 
    sessionTimeout.toMillis.toInt, 
    connectionTimeout.toMillis.toInt, 
    ZKStringSerializer
  )
  
  def this(props: ZkConnectProps)(implicit executionContext: ExecutionContext) {
    this(props.connectString, props.connectionTimeout, props.sessionTimeout)
  }
  
  def close() = {
    client.close()
  }
  
  def getBrokers: Future[Vector[KafkaBroker]] = {
    future {
      val brokerIds = client.getChildren(ZkUtils.BrokerIdsPath).map(_.toInt).sorted
      val brokers = brokerIds map { brokerId =>
        val path = s"${ZkUtils.BrokerIdsPath}/$brokerId"
        val stat = new Stat
        val json = client.readData[String](path, stat)
        val ast = json.asJson
        val info = ast.convertTo[BrokerInfo]
        KafkaBroker(brokerId, info.host, info.port)
      }
      brokers.toVector
    }
  }
   
  def getBroker(brokerId: Int): Future[KafkaBroker] = {
    future {
      val path = s"${ZkUtils.BrokerIdsPath}/$brokerId"
      val json = client.readData[String](path)
      val ast = json.asJson
      val info = ast.convertTo[BrokerInfo]
      KafkaBroker(brokerId, info.host, info.port)
    }
  }
 
  def getTopicNames: Future[Seq[String]] = {
    future {
      ZkUtils.getAllTopics(client).sorted
    }
  }
  
  def getConsumerGroups: Future[Seq[String]] = {
    future {
      ZkUtils.getChildren(client, ZkUtils.ConsumersPath).sorted
    }
  }
  
  def getConsumersInGroup(group: String): Future[Seq[String]] = {
    future {
      ZkUtils.getConsumersInGroup(client, group).sorted
    }
  }
  
  def getConsumerGroupTopics(group: String): Future[Seq[String]] = {
    future {
      val path = s"${ZkUtils.ConsumersPath}/$group/offsets"
      client.getChildren(path).sorted
    }
  }
  
  def getTopicPartitionIds(topic: String): Future[Seq[Int]] = {
    future {
     val path = s"${ZkUtils.BrokerTopicsPath}/$topic/partitions"
     client.getChildren(path).map(_.toInt).sorted
    }
  }
  
  /** Get all topic specific info */
  def getTopicInfo(topic: String): Future[KafkaTopic] = {
    
    def earliestAndLatestOffset(consumer: SimpleConsumer, partId: Int): PartitionOffsetRange = {
      val tap = TopicAndPartition(topic, partId)
      val earliest = consumer.earliestOrLatestOffset(tap, OffsetRequest.EarliestTime, Request.OrdinaryConsumerId)
      val latest   = consumer.earliestOrLatestOffset(tap, OffsetRequest.LatestTime,   Request.OrdinaryConsumerId)
      PartitionOffsetRange(earliest, latest)
    }
    
    def getTopicPartitions(consumers: Vector[BrokerConsumer], partsIds: Seq[Int]) = {
      partsIds map { partId =>
        val pathPart = s"${ZkUtils.BrokerTopicsPath}/$topic/partitions/$partId/state"
        val source = client.readData[String](pathPart, true)
        val ast = source.asJson
        val state = ast.convertTo[BrokerTopicPartitionState]
        
        val broker = consumers(state.leader)
        val range  = earliestAndLatestOffset(broker.consumer, partId)
        
        KafkaTopicPartition(partId, state.isr, state.leader, range.earliest, range.latest)
      }
    }
    
    for {
      brokers    <- getBrokers
      consumers  =  brokers.map(BrokerConsumer)
      partsIds   <- getTopicPartitionIds(topic)
      partitions =  getTopicPartitions(consumers, partsIds)
    } yield {
      consumers.foreach(_.consumer.close())
      KafkaTopic(topic, partitions.toVector)
    }
  }
  
  def getGroupTopicPartitionIds(group: String, topic: String): Future[Seq[Int]] = {
    future {
     val path = s"${ZkUtils.ConsumersPath}/$group/offsets/$topic"
     client.getChildren(path).map(_.toInt).sorted
    }
  }
  
  def getPartitionOffset(group: String, topic: String, partition: Int): Future[KafkaPartitionOffset] = {
    future {
      val path = s"${ZkUtils.ConsumersPath}/$group/offsets/$topic/$partition"
      val offset = client.readData[String](path).toLong
      KafkaPartitionOffset(partition, offset)
    }
  }
  
}

/**
 * This object can be used to make Kafka info requests w/o having to define a thread pool
 * or do connection management.
 */
object Utils {
  // Like Executors.newCachedThreadPool() except limited to 10 threads and 20s instead of 60s lifetime.
  //private lazy val threadPool = new ThreadPoolExecutor(0, 10, 20L, TimeUnit.SECONDS, new SynchronousQueue[Runnable])
  private lazy val threadPool = Executors.newCachedThreadPool()
  implicit lazy val executionContext = ExecutionContext.fromExecutor(threadPool)
  
  def apply(
    connectString: String, 
    sessionTimeout: FiniteDuration = 30.seconds,
    connectionTimeout: FiniteDuration = 30.seconds
  ): Utils = {
    new Utils(connectString, sessionTimeout, connectionTimeout)
  }
  
  def apply(props: ZkConnectProps): Utils = {
    new Utils(props)
  }

  /** Return a future for the list of brokers.
    * 
    * @return Future
    */
  def getBrokers(implicit props: ZkConnectProps): Future[Seq[KafkaBroker]] = {
    val zkutils = Utils(props)
    val future = zkutils.getBrokers
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }

  /** Return a future for the broker.
    * 
    * @return Future
    */
  def getBroker(brokerId: Int)(implicit props: ZkConnectProps): Future[KafkaBroker] = {
    val zkutils = Utils(props)
    val future = zkutils.getBroker(brokerId)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }

  def getTopicNames(implicit props: ZkConnectProps): Future[Seq[String]] = {
    val zkutils = Utils(props)
    val future = zkutils.getTopicNames
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }

  def getTopicPartitionIds(topicName: String)(implicit props: ZkConnectProps): Future[Seq[Int]] = {
    val zkutils = Utils(props)
    val future = zkutils.getTopicPartitionIds(topicName)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def getTopics(implicit props: ZkConnectProps): Future[Seq[KafkaTopic]] = {
    def getTopics(topicNames: Seq[String]) = {
      val futures = topicNames map { topicName =>
        getTopicInfo(topicName)
      }
      Future.sequence(futures)
    }
    
    for {
      topicNames <- getTopicNames
      topics <- getTopics(topicNames)
    } yield {
      topics
    }
  }

  def getConsumerGroups(implicit props: ZkConnectProps): Future[Seq[String]] = {
    val zkutils = Utils(props)
    val future = zkutils.getConsumerGroups
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }

  def getConsumersInGroup(group: String)(implicit props: ZkConnectProps): Future[Seq[String]] = {
    val zkutils = Utils(props)
    val future = zkutils.getConsumersInGroup(group)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def getConsumerGroupTopics(group: String)(implicit props: ZkConnectProps): Future[Seq[String]] = {
    val zkutils = Utils(props)
    val future = zkutils.getConsumerGroupTopics(group)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def getGroupTopicPartitionIds(group: String, topic: String)
    (implicit props: ZkConnectProps): Future[Seq[Int]] = {
    val zkutils = Utils(props)
    val future = zkutils.getGroupTopicPartitionIds(group, topic)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def getPartitionOffset(group: String, topic: String, partition: Int)
    (implicit props: ZkConnectProps): Future[KafkaPartitionOffset] = {
    val zkutils = Utils(props)
    val future = zkutils.getPartitionOffset(group, topic, partition)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def getTopicInfo(topic: String)(implicit props: ZkConnectProps): Future[KafkaTopic] = {
    val zkutils = Utils(props)
    val future = zkutils.getTopicInfo(topic)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
    
  def getGroupOffsets(group: String)(implicit props: ZkConnectProps): Future[KafkaGroupOffsets] = {
    def getPartitionOffsets(group: String, topicName: String, partIds: Seq[Int]) = {
      val futures = partIds map { partId =>
        getPartitionOffset(group, topicName, partId)
      }
      Future.sequence(futures)
    }
    
    def getTopicOffsets(topicNames: Seq[String]): Future[Seq[KafkaGroupTopicOffsets]] = {
      val futures = topicNames map { topicName =>
        for {
          partIds <- getGroupTopicPartitionIds(group, topicName)
          offsets <- getPartitionOffsets(group, topicName, partIds)
        } yield {
          KafkaGroupTopicOffsets(topicName, offsets.toVector)
        }
      }
      Future.sequence(futures)
    }
    
    for {
      topicNames <- getConsumerGroupTopics(group)
      topicOffsets <- getTopicOffsets(topicNames)
    } yield {
      val topicMap = (topicOffsets map {offsets => offsets.topic -> offsets}).toMap
      KafkaGroupOffsets(group, topicMap)
    }
  }
}

case class ZkConnectProps(
    connectString: String, 
    sessionTimeout: FiniteDuration = 30.seconds,
    connectionTimeout: FiniteDuration = 30.seconds
  )

private case class PartitionOffsetRange(earliest: Long, latest: Long)

private case class GroupTopicNames(group: String, topics: Seq[String])

private case class BrokerConsumer(broker: KafkaBroker) {
  private val timeout = 60.seconds.toMillis.toInt
  private val bufferSize = 100000
  private val clientName = "KafkaStatus"
  
  lazy val consumer = new SimpleConsumer(broker.host, broker.port, timeout, bufferSize, clientName)
}
