package nest.sparkle.loader.kafka

import nest.sparkle.test.SparkleTestConfig

/** use a custom .conf override (to specify log4j) */
trait KafkaTestConfig extends SparkleTestConfig {
  override def testConfigFile = Some("sparkle-kafka-tests")
}