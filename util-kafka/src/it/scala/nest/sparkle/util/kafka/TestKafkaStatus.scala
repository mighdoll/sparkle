package nest.sparkle.util.kafka

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.duration

/**
 * Test Utils.
 */
class TestKafkaStatus
  extends KafkaTestSuite
{
  private val _timeout = FiniteDuration(10, duration.MINUTES)  // WTF? 10.minutes doesn't compile
  implicit val zkProps = ZkConnectProps("localhost:2181", _timeout, _timeout)
  
  private def checkTestTopic(topic: KafkaTopic) {
    topic.name shouldBe TopicName
    topic.partitions.length shouldBe NumPartitions
    val partitions = topic.partitions
    partitions.zipWithIndex foreach { case (partition,i) =>
      partition.id shouldBe i
      partition.leader shouldBe 0
      partition.brokerIds.length shouldBe 1
      partition.brokerIds(0) shouldBe 0
      partition.earliest should contain (0)
      partition.latest should contain (5)
    }
  }
  
  test("get broker 0") {
    val future = KafkaStatus.brokerFromId(0)
    val broker = Await.result(future, timeout)
    
    broker.host shouldBe "localhost"
    broker.port shouldBe 9092
    broker.id   shouldBe 0
  }
  
  test("should be one broker") {
    val future = KafkaStatus.allBrokers
    val brokers = Await.result(future, timeout)
    
    brokers.length shouldBe 1
    val broker = brokers.head
    broker.host shouldBe "localhost"
    broker.port shouldBe 9092
    broker.id   shouldBe 0
  }
  
  test("topic names should contain the test one") {
    val future = KafkaStatus.allTopicNames
    val topicNames = Await.result(future, timeout)
    
    topicNames should contain (TopicName)
  }
  
  test("topics should contain the test one") {
    val future = KafkaStatus.allTopics
    val topicMap = Await.result(future, timeout)
    
    topicMap should contain key TopicName
    val topic = topicMap(TopicName)
    checkTestTopic(topic)
  }
   
  test("get the test topic") {
    val future = KafkaStatus.topicFromName(TopicName)
    val topic = Await.result(future, timeout)
    
    checkTestTopic(topic)
  }
  
  test("consumer groups should contain the test one") {
    val future = KafkaStatus.allConsumerGroups
    val groups = Await.result(future, timeout)
    
    groups.contains(ConsumerGroup) shouldBe true
  }
 
  test("should be one consumer in the consumer group") {
    val future = KafkaStatus.consumersInGroup(ConsumerGroup)
    val consumers = Await.result(future, timeout)
    
    consumers.length shouldBe 1
    val consumerId = consumers(0)
    consumerId shouldBe s"${ConsumerGroup}_$ConsumerId"
  }
  
  test("consumer group should contain topic") {
    val future = KafkaStatus.consumerGroupTopics(ConsumerGroup)
    val topics = Await.result(future, timeout)
    
    topics.contains(TopicName) shouldBe true
  }
   
  test("get consumer group topic partition 0 offset") {
    val future = KafkaStatus.kafkaPartitionOffset(ConsumerGroup, TopicName, 0)
    val partitionOffset = Await.result(future, timeout)
    
    partitionOffset.partition shouldBe 0
    partitionOffset.offset shouldBe 2
  }
   
  test("get consumer group topic offsets") {
    val future = KafkaStatus.consumerGroupOffsets(ConsumerGroup)
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
