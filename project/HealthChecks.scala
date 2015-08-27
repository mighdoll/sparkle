import org.apache.log4j.helpers.LogLog
import org.apache.log4j.xml.DOMConfigurator

import scala.util.Try
import scala.concurrent.duration._

import sbt.Def
import sbt.Keys.streams

import com.datastax.driver.core.Cluster

import kafka.api.TopicMetadataRequest
import kafka.consumer.SimpleConsumer
import kafka.utils.ZkUtils

import org.I0Itec.zkclient.ZkClient

/** Status check functions to verify that various servers are up and running */
object HealthChecks {

  /** log4j isn't initializing itself properly inside sbt, so initialize log4j manually. */
  lazy val setupLog4j: () => Unit = {
    val logConfig = getClass.getClassLoader.getResource("log4j.xml").getPath
    LogLog.setQuietMode(true)
    DOMConfigurator.configure(logConfig)
    () => Unit
  }

  /** return a function that will check whether the cassandra server is alive */
  val cassandraIsHealthy = Def.task[() => Try[Unit]] { () =>
    setupLog4j()
    Try {
      val builder = Cluster.builder()
      builder.addContactPoint("localhost")
      val cluster = builder.build()
      try {
        val session = cluster.connect()
        session.close()
      } finally {
        cluster.close()
      }
    }
  }

  /** return a function that will check whether the zookeeper service is alive */
  val zookeeperIsHealthy = Def.task[() => Try[Unit]] { () =>
    setupLog4j()
    Try {
      val log = streams.value.log
      
      val zkClient = new ZkClient("127.0.0.1:2181", 30000, 30000)
      try {
        // This gets Kafka topics if any exist.
        val topics = ZkUtils.getAllTopics(zkClient)
        log.debug(s"${topics.length} Kafka topics found in zookeeper")
      } finally {
        zkClient.close()
      }
    }
  }

  /** return a function that will check whether a local kafka server is alive */
  val kafkaIsHealthy = Def.task[() => Try[Unit]] { () =>
    setupLog4j()
    Try {
      val consumer = new SimpleConsumer(
        host = "127.0.0.1",
        port = 9092,
        soTimeout = 2.seconds.toMillis.toInt,
        bufferSize = 1024,
        clientId = "sbt-health-check")

      // this will fail if Kafka is unavailable
      consumer.send(new TopicMetadataRequest(Nil, 1))
    }
  }
}
