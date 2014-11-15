package nest.sparkle.util.kafka

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.duration

/**
 * Test Utils.
 */
class TestUtils
  extends KafkaTestSuite
{
  private val _timeout = FiniteDuration(10, duration.MINUTES)  // WTF? 10.minutes doesn't compile
  implicit val zkProps = ZkConnectProps("localhost:2181", _timeout, _timeout)
  
  test("get broker 0") {
    val future = Utils.getBroker(0)
    val broker = Await.result(future, timeout)
    
    broker.host shouldBe "localhost"
    broker.port shouldBe 9092
    broker.id   shouldBe 0
  }
  
  test("should be one broker") {
    val future = Utils.getBrokers
    val brokers = Await.result(future, timeout)
    
    brokers.length shouldBe 1
    val broker = brokers.head
    broker.host shouldBe "localhost"
    broker.port shouldBe 9092
    broker.id   shouldBe 0
  }
  
  test("topics should contain the test one") {
    val future = Utils.getTopicNames
    val topicNames = Await.result(future, timeout)
    
    topicNames.contains(TopicName) shouldBe true
  }
   
  test("get the test topic") {
    val future = Utils.getTopicInfo(TopicName)
    val topic = Await.result(future, timeout)
    
    topic.name shouldBe TopicName
    topic.partitions.length shouldBe NumPartitions
    val partitions = topic.partitions
    partitions.zipWithIndex foreach { case (partition,i) =>
      partition.id shouldBe i
      partition.leader shouldBe 0
      partition.brokerIds.length shouldBe 1
      partition.brokerIds(0) shouldBe 0
      partition.earliest shouldBe 0
      partition.latest shouldBe 5
    }
  }
   
  test("get the test topic partition ids") {
    val future = Utils.getTopicPartitionIds(TopicName)
    val partIds = Await.result(future, timeout)
    
    partIds.length shouldBe NumPartitions
    partIds.zipWithIndex foreach { case (partId,i) =>
      partId shouldBe i
    }
  }
  
  test("consumer groups should contain the test one") {
    val future = Utils.getConsumerGroups
    val groups = Await.result(future, timeout)
    
    groups.contains(ConsumerGroup) shouldBe true
  }
 
  test("should be one consumer in the consumer group") {
    val future = Utils.getConsumersInGroup(ConsumerGroup)
    val consumers = Await.result(future, timeout)
    
    consumers.length shouldBe 1
    val consumerId = consumers(0)
    consumerId shouldBe s"${ConsumerGroup}_$ConsumerId"
  }
  
  test("consumer group should contain topic") {
    val future = Utils.getConsumerGroupTopics(ConsumerGroup)
    val topics = Await.result(future, timeout)
    
    topics.contains(TopicName) shouldBe true
  }
   
  test("get consumer group topic partition 0 offset") {
    val future = Utils.getPartitionOffset(ConsumerGroup, TopicName, 0)
    val partitionOffset = Await.result(future, timeout)
    
    partitionOffset.partition shouldBe 0
    partitionOffset.offset shouldBe 2
  }
   
  test("get consumer group topic offsets") {
    val future = Utils.getGroupOffsets(ConsumerGroup)
    val groupOffsets = Await.result(future, timeout)
    
    groupOffsets.group shouldBe ConsumerGroup
    groupOffsets.topics.size shouldBe 1
    groupOffsets.topics.keySet.contains(TopicName) shouldBe true
    val topicOffsets = groupOffsets.topics(TopicName)
    topicOffsets.topic shouldBe TopicName
    topicOffsets.partitions.zipWithIndex foreach { case (partitionOffset,i) =>
      partitionOffset.partition shouldBe i
      partitionOffset.offset shouldBe 2
    }
  }

}
