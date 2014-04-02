/* Copyright 2014  Nest Labs

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

import org.scalatest.FunSuite
import org.scalatest.Matchers
import scala.concurrent.ExecutionContext
import org.apache.avro.generic.GenericData
import org.scalatest.prop.PropertyChecks
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import scala.collection.JavaConverters._
import nest.sparkle.util.ConfigUtil.modifiedConfig
import nest.sparkle.store.cassandra.ConfiguredCassandra
import nest.sparkle.util.Watch
import nest.sparkle.util.PublishSubject
import rx.lang.scala.Observer
import nest.sparkle.util.WatchReport
import spray.util._
import nest.sparkle.loader.kafka.AvroRecordGenerators.genArrayRecords

class TestStreamLoadAvroKafka extends FunSuite with Matchers with PropertyChecks
    with KafkaTestConfig with CassandraTestConfig {
  
  val testStore = new ConfiguredCassandra(cassandraConfig)

  def expectedPath(id: String): String = s"sample-data/path/$id/Latency"

  test("load a stream contains a few milliDouble arrays into cassandra") {
    import ExecutionContext.Implicits.global

    forAll(genArrayRecords(3), MinSuccessful(1)) { generatedRecords =>
      KafkaTestUtil.withTestAvroTopic(loaderConfig, MillisDoubleArrayAvro.schema) { kafka =>
        
        // prefill kafka queue
        kafka.writer.write(generatedRecords.map { _.record })

        // run loader
        val overrides =
          "topics" -> List(kafka.topic) ::
            "find-decoder" -> "nest.sparkle.loader.kafka.MillisDoubleArrayFinder" ::
            Nil
            
        val config = modifiedConfig(loaderConfig, overrides: _*)
        val loader = new AvroKafkaLoader(config, testStore)
        val watch = Watch[ColumnUpdate]()
        loader.watch(watch) // watch loading progress
        loader.load()

        val updates = watch.report.take(generatedRecords.length).toBlockingObservable.toList

        // verify update reports
        val generatedPaths = generatedRecords.map{ record => expectedPath(record.id) }
        updates.foreach { update =>
          generatedPaths.contains(update.columnPath) shouldBe true
        }

        // verify matching data in cassandra 
        generatedRecords.foreach { generated =>
          val column = testStore.column[Long, Double](expectedPath(generated.id)).await
          val results = column.readRange(None, None).toBlockingObservable.toList
          generated.events.size shouldBe results.length
          generated.events zip results map {
            case ((time, value), event) =>
              time shouldBe event.argument
              value shouldBe event.value
          }
        }
      }
    }
  }
}
