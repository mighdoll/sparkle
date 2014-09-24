package nest.sparkle.loader.kafka

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe.TypeTag.Long

import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks

import nest.sparkle.loader.kafka.AvroRecordGenerators.GeneratedRecord
import nest.sparkle.loader.kafka.KafkaTestUtil.withTestAvroTopic
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.util.ConfigUtil.{ modifiedConfig, sparkleConfigName }
import nest.sparkle.util.QuickTiming.printTime

class TestLargeKafkaStream extends FunSuite with Matchers with PropertyChecks with KafkaTestConfig with CassandraTestConfig {
  import AvroRecordGenerators._

  val id1 = "foo"
  val id2 = "bar"
  val columnPath = s"sample-data/path/$id1/$id2/Latency/value"

  val elementsPerRecord = 120
  val records = 1000

  /** run a test runction with a fixed threadpool available */
  def withFixedThreadPool[T](threads: Int = 10)(fn: ExecutionContext => T): T = {
    val execution = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(10))
    try {
      fn(execution)
    } finally {
      execution.shutdown()
    }
  }

  test("load 120K records reporting the time it takes to load them into cassandra") {
    val generated = manyRecords(id1, id2, elementsPerRecord, records)

    withTestAvroTopic(rootConfig, MillisDoubleArrayAvro.schema) { testTopic =>
      testTopic.writer.write(generated.map { _.record })

      withTestDb { testStore =>
        val overrides =
          s"$sparkleConfigName.kafka-loader.topics" -> List(testTopic.topic) ::
            s"$sparkleConfigName.kafka-loader.find-decoder" -> "nest.sparkle.loader.kafka.MillisDoubleArrayFinder" ::
            Nil

        val storeWrite = testStore.writeListener.listen[Long](columnPath)
        val modifiedRoot = modifiedConfig(rootConfig, overrides: _*)

        withFixedThreadPool() { implicit execution =>
          val loader = new AvroKafkaLoader[Long](modifiedRoot, testStore)

          val rows = records * elementsPerRecord
          printTime(s"loading $rows rows ($elementsPerRecord elements per record) takes:") {
            loader.start()
            storeWrite.take(generated.size).toBlocking.toList // await completion
          }
        }
      }

    }
  }

}