package nest.sparkle.util.kafka

import java.util.Properties

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import kafka.consumer.{ConsumerIterator, ConsumerConnector, Consumer, ConsumerConfig}
import kafka.producer.{Partitioner, KeyedMessage, Producer, ProducerConfig}
import kafka.utils.{VerifiableProperties, ZkUtils, ZKStringSerializer}
import kafka.serializer.StringDecoder
import kafka.admin.AdminUtils

import org.I0Itec.zkclient.ZkClient

import nest.sparkle.util.RandomUtil

/**
 * Test Utils.
 */
class TestUtils
  extends FunSuite
  with Matchers
  with BeforeAndAfterAll
{
  val TOPIC = "test-" + RandomUtil.randomAlphaNum(4)
  val CONSUMER_GROUP = s"group-$TOPIC"
  val CONSUMER_ID = "it"
  val NUM_PARTITIONS = 16
  
  val messages = (0 to 1000).map("message " + _)
  
  var consumer: Option[ConsumerConnector] = None
  var consumerIterators = Seq[ConsumerIterator[String,String]]()

  implicit val zkProps = ZkConnectProps("localhost:2181", 10.minutes, 10.minutes)
  
  //implicit val timeout = 3.seconds 
  implicit val timeout = 1.hours  // use when running with a debugger
  
  override def beforeAll(): Unit = {
    writeTopic(TOPIC, messages)
    setupConsumerGroup(CONSUMER_GROUP, TOPIC)
    super.beforeAll()
  }
  
  override def afterAll(): Unit = {
    consumerIterators = Seq[ConsumerIterator[String,String]]()
    consumer.map(_.shutdown())
    consumer = None
  }


  /** Write the messages to topic using the message number as the key.
    * The first message will be written to partition 1, second to 2, etc. modulus the
    * number of partitions (default 16).
    * 
    * @param name topic name
    * @param messages list of strings to write to the topic
    */
  protected def writeTopic(name: String, messages: Traversable[String]) = {
    
/*  Alas, this code doesn't work so we must spam the it kafka with random topics & consumers.
    val zkClient = new ZkClient(
      "localhost:2181", 30.seconds.toMillis.toInt, 30.seconds.toMillis.toInt, ZKStringSerializer
    )
    try {
      if (AdminUtils.topicExists(zkClient, name)) {
        //AdminUtils.deleteTopic(zkClient, name)
        // Do we need to wait here?
        zkClient.deleteRecursive(ZkUtils.getTopicPath(TOPIC));
      }
    } finally {
      zkClient.close()
    }
*/

    val producer = {
      val config = {
        val props = new Properties()
        props.put("metadata.broker.list", "localhost:9092")
        props.put("request.required.acks", "1")
        props.put("serializer.class", "kafka.serializer.StringEncoder")
        props.put("partitioner.class", "nest.sparkle.util.kafka.SparkleTestPartitioner")
        new ProducerConfig(props)
      }
      new Producer[String, String](config)
    }

    try {
      var messageKey = 0
      messages foreach { message =>
        val keyedMessage = new KeyedMessage[String,String](TOPIC,messageKey.toString,message)
        producer.send(keyedMessage)
        messageKey += 1
      }
    } finally {
      producer.close()
    }
    
  }
  
  protected def createConsumer(name: String): ConsumerConnector = {
    val props = new Properties()
    props.put("zookeeper.connect", "localhost:2181")
    props.put("zookeeper.connection.timeout.ms", "300000")
    props.put("zookeeper.session.timeout.ms", "600000")
    props.put("zookeeper.sync.time.ms", "2000")
    props.put("auto.commit.enable", "false")
    props.put("auto.offset.reset", "smallest")
    props.put("consumer.timeout.ms", "5000")
    props.put("consumer.timeout.ms", "-1")
    props.put("group.id", name)
    props.put("client.id", "it")
    props.put("consumer.id", CONSUMER_ID)
    val config = new ConsumerConfig(props)
    Consumer.create(config)
  }
  
  /** Create a consumer group and read a few messages from each partition to create offsets info */
  protected def setupConsumerGroup(groupName: String, topicName: String): Unit = {
    consumer = Some(createConsumer(groupName))
    try {
      val numPartitions = NUM_PARTITIONS
      val topicCountMap = Map(topicName -> numPartitions)
      val decoder = new StringDecoder
      val streamMap = consumer.get.createMessageStreams[String,String](topicCountMap, decoder, decoder)
      val streams = streamMap(topicName)
      
      // Read and validate the first two messages in each partition
      streams foreach { stream =>
        val iter = stream.iterator()
        consumerIterators = iter +: consumerIterators

        val mmd = iter.next()
        mmd.topic shouldBe topicName
        val partId = mmd.partition
        val text = mmd.message()
        text shouldNot be (null)
        text.length > 0 shouldBe true

        val n = partId % numPartitions
        text shouldBe s"message $n"
        
        val mmd2 = iter.next()  // make offset 2
        mmd2.message() shouldBe s"message ${n + numPartitions}"
      }
   } finally {
      consumer.map(_.commitOffsets)
    }
  }
  
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

class SparkleTestPartitioner(props: VerifiableProperties) 
  extends Partitioner
{
  def partition(key: Any, num_partitions: Int): Int = {
    try {
      val n = key.toString.toInt
      n % num_partitions
    } catch {
      case NonFatal(err)  => 0
    }
  }
}
