package nest.sparkle.util.kafka

import spray.json.DefaultJsonProtocol

/**
 * Json protocol to serialize/deserialize util info objects.
 */
object KafkaJsonProtocol
  extends DefaultJsonProtocol
{
  implicit val brokerFormat = jsonFormat3(KafkaBroker.apply)
}
