import java.io.{ BufferedReader, InputStreamReader, PrintWriter }
import java.net.{ InetAddress, Socket }

import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
import javax.management.ObjectName

import scala.util.Try

import sbt.Def
import sbt.Keys.streams

import kafka.api.TopicMetadataRequest
import kafka.consumer.SimpleConsumer

import BackgroundServiceKeys.jmxPort

/** Status check functions to verify that various servers are up and running */
object HealthChecks {

  /** return a function that will check whether the cassandra server is alive */
  val cassandraIsHealthy = Def.task[() => Try[Boolean]] { () =>
    Try {
      val log = streams.value.log

      jmxPort.value match {
        case None =>
          log.warn("configure Cassandra with a JMX port to enable cassandra health check")
          false
        case Some(port) =>
          val url = new JMXServiceURL(s"service:jmx:rmi://localhost:$port/jndi/rmi://localhost:$port/jmxrmi")
          val connection = JMXConnectorFactory.connect(url, null)

          try {
            val client = connection.getMBeanServerConnection

            client.getAttribute(
              new ObjectName("org.apache.cassandra.metrics:type=Compaction,name=CompletedTasks"),
              "Value")
              
            Thread.sleep(10000) // TODO fix me better. I think we need to make a real connection rather than this jmx stuff
          } finally {
            connection.close()
          }

          true
      }
    }
  }

  /** return a function that will check whether the zookeeper service is alive */
  val zookeeperIsHealthy = Def.task[() => Try[Boolean]] { () =>
    Try {
      val localhost = InetAddress.getByAddress(Array[Byte](127, 0, 0, 1))

      val socket = new Socket(localhost, 2181)
      val out = new PrintWriter(socket.getOutputStream, true)
      val in = new BufferedReader(new InputStreamReader(socket.getInputStream))

      out.println("stat")
      val status = in.readLine

      status.startsWith("Zookeeper version")
    }
  }

  /** return a function that will check whether a local kafka server is alive */
  val kafkaIsHealthy = Def.task[() => Try[Boolean]] { () =>
    Try {
      val consumer = new SimpleConsumer(
        host = "127.0.0.1",
        port = 9092,
        soTimeout = 1000 /*ms*/ ,
        bufferSize = 1024,
        clientId = "sbt-health-check")

      // this will fail if Kafka is unavailable
      consumer.send(new TopicMetadataRequest(Nil, 1))
      true
    }
  }
}
