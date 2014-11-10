package nest.sparkle.util.kafka

import java.util.Properties

import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import kafka.consumer.{ConsumerIterator, ConsumerConnector, Consumer, ConsumerConfig}
import kafka.producer.{Partitioner, KeyedMessage, Producer, ProducerConfig}
import kafka.utils.VerifiableProperties

import kafka.serializer.StringDecoder

import nest.sparkle.util.RandomUtil

/**
 * Test Utils.
 */
trait KafkaTestSuite
  extends FunSuite
  with Matchers
  with BeforeAndAfterAll
{
  val TOPIC = "test-" + RandomUtil.randomAlphaNum(4)
  val CONSUMER_GROUP = s"itConsumer-$TOPIC"
  val CONSUMER_ID = "it"
  val NUM_PARTITIONS = 16
  
  val messages = (0 to 1000).map("message " + _)
  
  var consumer: Option[ConsumerConnector] = None
  var consumerIterators = Seq[ConsumerIterator[String,String]]()

  implicit val zkProps: ZkConnectProps
  
  //implicit val timeout = 3.seconds 
  implicit val timeout = 1.hours  // use when running with a debugger
  
  override protected def beforeAll(): Unit = {
    writeTopic(TOPIC, messages)
    setupConsumerGroup(CONSUMER_GROUP, TOPIC)
    super.beforeAll()
  }
  
  override protected def afterAll(): Unit = {
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
