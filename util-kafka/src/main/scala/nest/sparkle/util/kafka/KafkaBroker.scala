package nest.sparkle.util.kafka

/** Would have used kafka.cluster.Broker but that's private[kafka] now */
case class KafkaBroker(id: Int, host: String, port: Int) {
  override def toString: String = "id:" + id + ",host:" + host + ",port:" + port
}
