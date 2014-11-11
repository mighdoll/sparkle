package nest.sparkle.util.kafka

import java.util.Properties

import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import kafka.common.FailedToSendMessageException
import kafka.consumer.{ConsumerIterator, ConsumerConnector, Consumer, ConsumerConfig}
import kafka.producer.{Partitioner, KeyedMessage, Producer, ProducerConfig}
import kafka.utils.VerifiableProperties

import kafka.serializer.StringDecoder

import nest.sparkle.util.RandomUtil
import nest.sparkle.util.Log

/**
 * Use to add known state of kafka topic and consumer groups.
 */
trait KafkaTestSuite
  extends FunSuite
  with Matchers
  with BeforeAndAfterAll
  with Log
{
  val TopicName = "test-" + RandomUtil.randomAlphaNum(4)
  val ConsumerGroup = s"itConsumer-$TopicName"
  val ConsumerId = "it"
  val NumPartitions = 16
  
  private val SendMaxRetries = 20
  private val SendRetryWait = 10L
  
  val messages = (0 to 5*NumPartitions).map("message " + _)
  
  var consumer: Option[ConsumerConnector] = None
  var consumerIterators = Seq[ConsumerIterator[String,String]]()

  implicit val zkProps: ZkConnectProps
  
  //implicit val timeout = 3.seconds 
  implicit val timeout = 1.hours  // use when running with a debugger
  
  /** Create an immutable Kafka topic & consumer group status to run tests against. */
  override protected def beforeAll(): Unit = {
    writeTopic(TopicName, messages)
    setupConsumerGroup(ConsumerGroup, TopicName)
    super.beforeAll()
  }
  
  /** Teardown any open connections, iterators, etc. after tests run */
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
        props.put("message.send.max.retries", "20")
        props.put("retry.backoff.ms", "200")
        new ProducerConfig(props)
      }
      new Producer[String, String](config)
    }
    
    def send(message: KeyedMessage[String,String]): Unit = {
        var count = SendMaxRetries
        while (true) {
          try {
            producer.send(message)
            return
          } catch {
            case e: FailedToSendMessageException  => 
              count -= 1
              if (count == 0) throw e
              log.warn(s"kafka producer send failed ${SendMaxRetries-count} times, retrying")
              Thread.sleep(SendRetryWait)
          }
        }
    }

    try {
      var messageKey = 0
      messages foreach { message =>
        val keyedMessage = new KeyedMessage[String,String](TopicName,messageKey.toString,message)
        send(keyedMessage)
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
    props.put("consumer.id", ConsumerId)
    val config = new ConsumerConfig(props)
    Consumer.create(config)
  }
  
  /** Create a consumer group and read a few messages from each partition to create offsets info */
  protected def setupConsumerGroup(groupName: String, topicName: String): Unit = {
    consumer = Some(createConsumer(groupName))
    try {
      val numPartitions = NumPartitions
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
