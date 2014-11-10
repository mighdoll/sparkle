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
    
    topicNames.contains(TOPIC) shouldBe true
  }
   
  test("get the test topic") {
    val future = Utils.getTopicInfo(TOPIC)
    val topic = Await.result(future, timeout)
    
    topic.name shouldBe TOPIC
    topic.partitions.length shouldBe NUM_PARTITIONS
    val partitions = topic.partitions
    partitions.zipWithIndex foreach { case (partition,i) =>
      partition.id shouldBe i
      partition.leader shouldBe 0
      partition.brokerIds.length shouldBe 1
      partition.brokerIds(0) shouldBe 0
    }
  }
   
  test("get the test topic partition ids") {
    val future = Utils.getTopicPartitionIds(TOPIC)
    val partIds = Await.result(future, timeout)
    
    partIds.length shouldBe NUM_PARTITIONS
    partIds.zipWithIndex foreach { case (partId,i) =>
      partId shouldBe i
    }
  }
  
  test("consumer groups should contain the test one") {
    val future = Utils.getConsumerGroups
    val groups = Await.result(future, timeout)
    
    groups.contains(CONSUMER_GROUP) shouldBe true
  }
 
  test("should be one consumer in the consumer group") {
    val future = Utils.getConsumersInGroup(CONSUMER_GROUP)
    val consumers = Await.result(future, timeout)
    
    consumers.length shouldBe 1
    val consumerId = consumers(0)
    consumerId shouldBe s"${CONSUMER_GROUP}_$CONSUMER_ID"
  }
  
  test("consumer group should contain topic") {
    val future = Utils.getConsumerGroupTopics(CONSUMER_GROUP)
    val topics = Await.result(future, timeout)
    
    topics.contains(TOPIC) shouldBe true
  }
   
  test("get consumer group topic partition 0 offset") {
    val future = Utils.getPartitionOffset(CONSUMER_GROUP, TOPIC, 0)
    val partitionOffset = Await.result(future, timeout)
    
    partitionOffset.partition shouldBe 0
    partitionOffset.offset shouldBe 2
  }
   
  test("get consumer group topic offsets") {
    val future = Utils.getGroupOffsets(CONSUMER_GROUP)
    val groupOffsets = Await.result(future, timeout)
    
    groupOffsets.group shouldBe CONSUMER_GROUP
    groupOffsets.topics.size shouldBe 1
    groupOffsets.topics.keySet.contains(TOPIC) shouldBe true
    val topicOffsets = groupOffsets.topics(TOPIC)
    topicOffsets.topic shouldBe TOPIC
    topicOffsets.partitions.zipWithIndex foreach { case (partitionOffset,i) =>
      partitionOffset.partition shouldBe i
      partitionOffset.offset shouldBe 2
    }
  }

}
