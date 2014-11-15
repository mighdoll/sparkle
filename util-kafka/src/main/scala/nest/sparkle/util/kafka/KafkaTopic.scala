package nest.sparkle.util.kafka

case class KafkaTopic(name: String, partitions: Vector[KafkaTopicPartition]) {
  override def toString: String = name
}

case class KafkaTopicPartition(
  id: Int, 
  brokerIds: Seq[Int], 
  leader: Int,
  earliest: Long,
  latest: Long
) {
  override def toString: String = s"$id:$leader($brokerIds)"
}

case class KafkaGroupOffsets(
  group: String, 
  topics: Map[String,KafkaGroupTopicOffsets]
  ) {
  override def toString: String = s"$group"
}

case class KafkaGroupTopicOffsets(
  topic: String,
  partitions: Vector[KafkaPartitionOffset]
  ) {
  override def toString: String = s"$topic"
}

case class KafkaPartitionOffset(
  partition: Int,
  offset: Long
  ) extends Ordered[KafkaPartitionOffset] {
  override def toString: String = s"$partition:$offset"
  
  def compare(that: KafkaPartitionOffset): Int = {
    this.partition - that.partition
  }
}