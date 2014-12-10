/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */
package nest.sparkle.loader.kafka

import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Await, promise}
import scala.concurrent.duration._

import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks

import nest.sparkle.loader.kafka.KafkaTestUtil.withTestEncodedTopic
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.util.ConfigUtil.{ modifiedConfig, sparkleConfigName }
import nest.sparkle.util.QuickTiming.printTime

class TestLargeKafkaStream extends FunSuite with Matchers with PropertyChecks with KafkaTestConfig with CassandraTestConfig {
  import MillisDoubleTSVGenerators._

  val id1 = "foo"
  val id2 = "bar"
  val columnPath = s"sample-data/path/$id1/$id2/Latency/value"

  val elementsPerRecord = 120
  val records = 1000

  /** run a test function with a fixed threadpool available */
  def withFixedThreadPool[T](threads: Int = 10)(fn: ExecutionContext => T): T = {
    val execution = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(threads))
    try {
      fn(execution)
    } finally {
      execution.shutdown()
    }
  }

  test("load 120K records reporting the time it takes to load them into cassandra") {
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

        val storeWrite = testStore.writeListener.listen[Long](columnPath)
        val modifiedRoot = modifiedConfig(rootConfig, overrides: _*)

        withFixedThreadPool() { implicit execution =>
          val loader = new KafkaLoader[Long](modifiedRoot, testStore)
          
          // Set up a future that will complete when a notification for the last write is received
          val p = promise[Unit]()
          storeWrite.subscribe { update =>
            if (update.end == lastKey) {
              p.success(())
            }
          }
          val writesDone = p.future

          val rows = records * elementsPerRecord
          printTime(s"loading $rows rows ($elementsPerRecord elements per record) takes:") {
            loader.start()
            try {
              Await.ready[Unit](writesDone, 20.seconds)
            } finally {
              loader.shutdown()
            }
          }
        }
      }

    }
  }

}