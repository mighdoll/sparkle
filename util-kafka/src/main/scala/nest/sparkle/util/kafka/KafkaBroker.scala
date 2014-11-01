package nest.sparkle.util.kafka

import kafka.common.{BrokerNotAvailableException, KafkaException}
import kafka.utils.Json

/** Would have used kafka.cluster.Broker but that's private[kafka] now */
case class KafkaBroker(id: Int, host: String, port: Int) {
  override def toString: String = "id:" + id + ",host:" + host + ",port:" + port
}

object KafkaBroker {
  /** Create from json string stored in zookeeper
    * Copied from kafka.cluster.Broker.createBroker
    */
  def apply(id: Int, json: String): KafkaBroker = {
    if (json == null)
      throw new BrokerNotAvailableException("Broker id %s does not exist".format(id))
    try {
      Json.parseFull(json) match {
        case Some(m) =>
          val brokerInfo = m.asInstanceOf[Map[String, Any]]
          val host = brokerInfo.get("host").get.asInstanceOf[String]
          val port = brokerInfo.get("port").get.asInstanceOf[Int]
          new KafkaBroker(id, host, port)
        case None =>
          throw new BrokerNotAvailableException("Broker id %d does not exist".format(id))
      }
    } catch {
      case t: Throwable => throw new KafkaException("Failed to parse the broker info from zookeeper: " + json, t)
    }
  }
}
