package nest.sparkle.util.kafka

import spire.syntax.group

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future, future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.control.Exception.nonFatalCatch
import scala.util.{Failure, Success}

import spray.json._

import org.I0Itec.zkclient.ZkClient
import org.apache.zookeeper.data.Stat

import kafka.common.{TopicAndPartition, UnknownTopicOrPartitionException, NotLeaderForPartitionException}
import kafka.consumer.SimpleConsumer
import kafka.api.{OffsetRequest, Request}
import kafka.utils.{ZkUtils, ZKStringSerializer}

import nest.sparkle.util.Log
import nest.sparkle.measure.Span

import KafkaJsonProtocol._

/**
 * Functions to get Kafka information
 * 
 * @param connectString Zookeeper connect string, e.g. "localhost:2181"
 * @param connectionTimeout Zookeeper connection timeout
 * @param sessionTimeout Zookeeper session timeout
 * @param executionContext Thread pool to use for synchronous Zookeeper requests.
 */
class KafkaStatus(
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
  
  def allBrokers(implicit parentSpan: Span): Future[Seq[KafkaBroker]] = {
    future {
      val span = Span("allBrokers")
      span.time {
        val brokerIds = Span("BrokerIds")(span).time {
          client.getChildren(ZkUtils.BrokerIdsPath).map(_.toInt).sorted
        }
        val brokers = brokerIds map { brokerId =>
          Span("BrokerInfo")(span).time {
            val path = s"${ZkUtils.BrokerIdsPath}/$brokerId"
            val stat = new Stat
            val json = client.readData[String](path, stat)
            val ast = json.asJson
            val info = ast.convertTo[BrokerInfo]
            KafkaBroker(brokerId, info.host, info.port)
          }
        }
        brokers
      }
    }
  }
   
  def brokerFromId(brokerId: Int)(implicit parentSpan: Span): Future[KafkaBroker] = {
    future {
      Span("brokerFromId").time {
        val path = s"${ZkUtils.BrokerIdsPath}/$brokerId"
        val json = client.readData[String](path)
        val ast = json.asJson
        val info = ast.convertTo[BrokerInfo]
        KafkaBroker(brokerId, info.host, info.port)
      }
    }
  }
 
  def allTopicNames(implicit parentSpan: Span): Future[Seq[String]] = {
    future {
      Span("allTopicNames").time {
        ZkUtils.getAllTopics(client).sorted
      }
    }
  }
  
  def allConsumerGroups(implicit parentSpan: Span): Future[Seq[String]] = {
    future {
      Span("allConsumerGroups").time {
        ZkUtils.getChildren(client, ZkUtils.ConsumersPath).sorted
      }
    }
  }
  
  def consumersInGroup(group: String)(implicit parentSpan: Span): Future[Seq[String]] = {
    future {
      Span("consumersInGroup").time {
        ZkUtils.getConsumersInGroup(client, group).sorted
      }
    }
  }
  
  def consumerGroupTopics(group: String)(implicit parentSpan: Span): Future[Seq[String]] = {
    future {
      Span("consumerGroupTopics").time {
        val path = s"${ZkUtils.ConsumersPath}/$group/offsets"
        client.getChildren(path).sorted
      }
    }
  }
 
  def allTopics(implicit parentSpan: Span): Future[Map[String,KafkaTopic]] = {
    def topicsFromNames(consumers: Map[Int,BrokerConsumer], topicNames: Seq[String]) = {
       val futures = topicNames map { topicName =>
         future {
           kafkaTopic(consumers, topicName)
         }
      }
      Future.sequence(futures)
    }
    
    val futureTopicNames = allTopicNames
    val futureConsumers = simpleConsumers
    val futureResult =
      for {
        consumers  <- futureConsumers
        topicNames <- futureTopicNames
        topics     <- topicsFromNames(consumers, topicNames)
      } yield {
        topics.map(topic => topic.name -> topic).toMap
      }
    
    // Ensure SimpleConsumers are closed.
    futureResult onComplete { _ =>
      futureConsumers.map { consumers =>
        consumers.values.foreach(_.consumer.close())
      }
    }
    
    futureResult
  }
  
  /** Get all topic specific info */
  def topicFromName(topicName: String)
    (implicit parentSpan: Span): Future[KafkaTopic] = {
    val futureConsumers = simpleConsumers
    val futureResult =
      for {
        consumers <- futureConsumers
        topic     =  kafkaTopic(consumers, topicName)
      } yield {
        topic
      }
    
    // Ensure SimpleConsumers are closed.
    futureResult onComplete { _ =>
      futureConsumers map { consumers =>
        consumers.values.foreach(_.consumer.close())
      }
    }
  
    futureResult
  }
  
  def partitionIdsForConsumerGroupTopic(group: String, topic: String)
    (implicit parentSpan: Span): Future[Seq[Int]] = {
    future {
     Span("partitionIdsForConsumerGroupTopic").time {
       val path = s"${ZkUtils.ConsumersPath}/$group/offsets/$topic"
       client.getChildren(path).map(_.toInt).sorted
     }
    }
  }
  
  def kafkaPartitionOffset(group: String, topic: String, partition: Int)
    (implicit parentSpan: Span): Future[KafkaPartitionOffset] = {
    future {
      Span("kafkaPartitionOffset").time {
        val path = s"${ZkUtils.ConsumersPath}/$group/offsets/$topic/$partition"
        val offset = nonFatalCatch opt { client.readData[String](path).toLong }
        KafkaPartitionOffset(partition, offset)
      }
    }
  }
   
  private def partitionIdsForTopicName(topicName: String)
    (implicit parentSpan: Span): Seq[Int] = {
    Span("partitionIdsForTopicName").time {
      val path = s"${ZkUtils.BrokerTopicsPath}/$topicName/partitions"
      client.getChildren(path).map(_.toInt).sorted
    }
  }
  
  /** Get all topic specific info */
  private def kafkaTopic(consumers: Map[Int,BrokerConsumer], topicName: String)
    (implicit parentSpan: Span): KafkaTopic = {
    val span = Span("kafkaTopic")
    span.time {
      val partsIds = partitionIdsForTopicName(topicName)(span)
      val partitions = topicPartitions(consumers, topicName, partsIds)(span)
      KafkaTopic(topicName, partitions.toVector)
    }
  }
    
  private def topicPartitions(consumers: Map[Int,BrokerConsumer], topicName: String, partsIds: Seq[Int])
      (implicit parentSpan: Span) = {
    val span = Span("topicPartitions")
    span.time {
      partsIds map { partId =>
        val pathPart = s"${ZkUtils.BrokerTopicsPath}/$topicName/partitions/$partId/state"
        val source = client.readData[String](pathPart, true)
        val ast = source.asJson
        val state = ast.convertTo[BrokerTopicPartitionState]

        val broker = consumers(state.leader)
        val range = earliestAndLatestOffset(broker.consumer, topicName, partId)(span)

        KafkaTopicPartition(partId, state.isr, state.leader, range.earliest, range.latest)
      }
    }
  }

  private def earliestAndLatestOffset(
    consumer: SimpleConsumer, topicName: String, partId: Int
  )(implicit parentSpan: Span): PartitionOffsetRange =
  {
    Span("earliestAndLatestOffset").time {
      val tap = TopicAndPartition(topicName, partId)
      try {
        val earliest = consumer
          .earliestOrLatestOffset(tap, OffsetRequest.EarliestTime, Request.OrdinaryConsumerId)
        val latest = consumer
          .earliestOrLatestOffset(tap, OffsetRequest.LatestTime, Request.OrdinaryConsumerId)
        PartitionOffsetRange(Some(earliest), Some(latest))
      } catch {
        case e: UnknownTopicOrPartitionException =>
          log.error(s"Unknown topic/partId $topicName::$partId")
          PartitionOffsetRange(None, None)
        case e: NotLeaderForPartitionException   =>
          log.error(s"${consumer.host} not leader for $topicName::$partId")
          PartitionOffsetRange(None, None)
      }
    }
  }
  
  private def simpleConsumers(implicit parentSpan: Span): Future[Map[Int,BrokerConsumer]] = {
    val span = Span("simpleConsumers").start()
    val futureResult = for {
      brokers <- allBrokers(span)
      consumers = brokers.map(BrokerConsumer).map { bc => bc.broker.id -> bc}.toMap
    } yield {
      consumers
    }
    
    /** Ensure the span is completed before caller gets result */
    futureResult.map { result =>
      span.complete()
      result
    }.recoverWith {
      case NonFatal(err)  => 
        span.complete()
        Future.failed(err)
    }
  }
}

/**
 * This object can be used to make Kafka status requests w/o having to do connection management.
 */
object KafkaStatus {
  def apply(
    connectString: String, 
    sessionTimeout: FiniteDuration = 30.seconds,
    connectionTimeout: FiniteDuration = 30.seconds
  )(implicit executionContext: ExecutionContext): KafkaStatus = {
    new KafkaStatus(connectString, sessionTimeout, connectionTimeout)
  }
  
  def apply(props: ZkConnectProps)(implicit executionContext: ExecutionContext): KafkaStatus = {
    new KafkaStatus(props)
  }

  /** Return a future for the list of brokers.
    * 
    * @return Future
    */
  def allBrokers(implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[Seq[KafkaBroker]] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.allBrokers
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }

  /** Return a future for the broker.
    * 
    * @return Future
    */
  def brokerFromId(brokerId: Int)
      (implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[KafkaBroker] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.brokerFromId(brokerId)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }

  def allTopicNames(implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[Seq[String]] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.allTopicNames
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  /** Faster way of getting all topics. Maximizes parallel processing */
  def allTopics(implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[Map[String,KafkaTopic]] = {
    def topicsFromNames(topicNames: Seq[String]) = {
      val futures = topicNames map { topicName =>
        topicFromName(topicName)
      }
      Future.sequence(futures)
    }
    
    for {
      topicNames <- allTopicNames
      topics     <- topicsFromNames(topicNames)
    } yield {
      (topics map { topic => topic.name -> topic }).toMap
    }
  }
  
  /** This is slower than allTopics but uses fewer connections and threads */
  def allTopics2(implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[Map[String,KafkaTopic]] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.allTopics
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }

  def allConsumerGroups(implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[Seq[String]] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.allConsumerGroups
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }

  def consumersInGroup(group: String)
      (implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[Seq[String]] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.consumersInGroup(group)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def consumerGroupTopics(group: String)
      (implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[Seq[String]] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.consumerGroupTopics(group)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def partitionIdsForConsumerGroupTopic(group: String, topic: String)
    (implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[Seq[Int]] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.partitionIdsForConsumerGroupTopic(group, topic)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def kafkaPartitionOffset(group: String, topic: String, partition: Int)
    (implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): 
  Future[KafkaPartitionOffset] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.kafkaPartitionOffset(group, topic, partition)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
  
  def topicFromName(topicName: String)
      (implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[KafkaTopic] = {
    val zkutils = KafkaStatus(props)
    val future = zkutils.topicFromName(topicName)
    future onComplete { _ =>
      zkutils.close()
    }
    future
  }
    
  def consumerGroupOffsets(group: String)
      (implicit props: ZkConnectProps, executionContext: ExecutionContext, parentSpan: Span): Future[KafkaGroupOffsets] = {
    def offsetsForTopic(topicName: String, partIds: Seq[Int]) = {
      val futures = partIds map { partId =>
        kafkaPartitionOffset(group, topicName, partId)
      }
      Future.sequence(futures)
    }
    
    def offsetsForTopicNames(topicNames: Seq[String]): Future[Seq[KafkaGroupTopicOffsets]] = {
      val futures = topicNames map { topicName =>
        for {
          partIds <- partitionIdsForConsumerGroupTopic(group, topicName)
          offsets <- offsetsForTopic(topicName, partIds)
        } yield {
          KafkaGroupTopicOffsets(topicName, offsets.toVector)
        }
      }
      Future.sequence(futures)
    }
    
    for {
      topicNames   <- consumerGroupTopics(group)
      topicOffsets <- offsetsForTopicNames(topicNames)
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

/** To return partition offsets */
private case class PartitionOffsetRange(earliest: Option[Long], latest: Option[Long])

/** To return the topic names in a consumer group */
private case class GroupTopicNames(group: String, topics: Seq[String])

/** To store a SimpleConsumer for a broker */
private case class BrokerConsumer(broker: KafkaBroker) {
  private val timeout = 60.seconds.toMillis.toInt
  private val bufferSize = 100000
  private val clientName = "KafkaStatus"
  
  lazy val consumer = new SimpleConsumer(broker.host, broker.port, timeout, bufferSize, clientName)
}
