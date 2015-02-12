package nest.sparkle.loader.kafka

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Await, promise}
import scala.concurrent.duration._
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks
import nest.sparkle.loader.kafka.KafkaTestUtil.withTestEncodedTopic
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.util.ConfigUtil.{ modifiedConfig, sparkleConfigName }
import nest.sparkle.util.QuickTiming.printTime
import nest.sparkle.store.ColumnUpdate

class TestLargeKafkaStream 
  extends FunSuite 
          with Matchers 
          with PropertyChecks 
          with CassandraStoreTestConfig 
          with KafkaTestConfig 
{
  import MillisDoubleTSVGenerators._

  val id1 = "foo"
  val id2 = "bar"
  val columnPath = s"sample-data/path/$id1/$id2/Latency/value"

  val elementsPerRecord = 0  // not used in this suite
  val records = 50000

  /** run a test function with a fixed threadpool available */
  def withFixedThreadPool[T](threads: Int = 10)(fn: ExecutionContext => T): T = {
    val execution = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(threads))
    try {
      fn(execution)
    } finally {
      execution.shutdown()
    }
  }

  test(s"load $records records reporting the time it takes to load them into cassandra") {
    val generated = manyRecords(id1, id2, elementsPerRecord, records)
    val serde = MillisDoubleTSVSerde
    
    val lastKey = generated.last.key  // This will need to be fixed when manyRecords is fixed.

    withTestEncodedTopic(rootConfig, serde) { testTopic =>
      testTopic.writer.write(generated)

      withTestDb { testStore =>
        val overrides =
          s"$sparkleConfigName.kafka-loader.topics" -> List(testTopic.topic) ::
            s"$sparkleConfigName.kafka-loader.find-decoder" -> "nest.sparkle.loader.kafka.MillisDoubleTSVFinder" ::
            Nil

        val storeWrite = testStore.writeListener.listen(columnPath)
        val modifiedRoot = modifiedConfig(rootConfig, overrides: _*)

        withTestActors { implicit system =>
          withFixedThreadPool() { implicit execution =>
            val loader = new KafkaLoader[Long](modifiedRoot, testStore)

            // Set up a future that will complete when a notification for the last write is received
            val p = promise[Unit]()
            storeWrite.subscribe { update =>
              update match {
                case update:ColumnUpdate[_] if update.end == lastKey => p.success(())
                case _ => 
              }
            }
            val writesDone = p.future

            printTime(s"TestLargeKafkaStream: loading $records from kafka into cassandra: ") {
              loader.start()
              try {
                Await.ready[Unit](writesDone, 2.minutes)
              } finally {
                loader.shutdown()
              }
            }
          }
        }
      }

    }
  }

}