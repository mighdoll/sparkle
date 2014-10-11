package com.nestlabs.kafka

import org.apache.log4j.Logger
import org.apache.log4j.jmx.LoggerDynamicMBean

import kafka.server.{KafkaConfig, KafkaServerStartable, KafkaServer}
import kafka.utils.Utils

import java.util.Properties

object Main {
  private val logger = Logger.getLogger(Main.getClass)

  def main(args: Array[String]): Unit = {

    // this is magic from core/src/main/scala/kafka/Kafka.scala in Kafka distribution
    val kafkaLog4jMBeanName = "kafka:type=kafka.KafkaLog4j"
    Utils.swallow(logger.warn, Utils.registerMBean(new LoggerDynamicMBean(Logger.getRootLogger()), kafkaLog4jMBeanName))

    val props = Option(this.getClass().getClassLoader().getResourceAsStream("server.properties")).map { propStream => {
      try {
        val props = new Properties()
        props.load(propStream)
        props
      } finally {
        propStream.close()
      }
    }}.getOrElse { sys.error("no server.properties file found on classpath") }

    
    val server = new KafkaServerStartable(new KafkaConfig(props))
    
    server.startup
    server.awaitShutdown
  }
}
