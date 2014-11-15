package nest.sparkle.util.kafka

import spray.json.DefaultJsonProtocol

/**
 * Json protocol to serialize/deserialize util info objects.
 */
object KafkaJsonProtocol
  extends DefaultJsonProtocol
{
  implicit val brokerInfoFormat = jsonFormat5(BrokerInfo)
  implicit val brokerFormat = jsonFormat3(KafkaBroker)
  
  implicit val topicPartitionFormat = jsonFormat5(KafkaTopicPartition)
  implicit val topicFormat = jsonFormat2(KafkaTopic)
  
  implicit val partitionOffsetFormat = jsonFormat2(KafkaPartitionOffset)
  implicit val topicOffsetsFormat = jsonFormat2(KafkaGroupTopicOffsets)
  implicit val groupTopicOffsetsFormat = jsonFormat2(KafkaGroupOffsets)
  
  implicit val partitionStateFormat = jsonFormat4(BrokerTopicPartitionState)
}

/** To parse JSON stored as node value in zookeeper for the topic's partition state. */
private[kafka] case class BrokerTopicPartitionState(
  controller_epoch: Int,
  isr: Seq[Int],
  leader: Int,
  version: Int
  )

/** To parse JSON stored as node value in zookeeper for the broker. */
private[kafka] case class BrokerInfo(
  jmx_port: Int,
  timestamp: Option[String] = None,
  host: String,
  port: Int,
  version: Int
  )
