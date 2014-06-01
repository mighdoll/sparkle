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
import nest.sparkle.util.Watch
import nest.sparkle.util.PublishSubject
import rx.lang.scala.Observer
import nest.sparkle.util.WatchReport
import spray.util._
import nest.sparkle.loader.kafka.AvroRecordGenerators.genArrayRecords
import nest.sparkle.util.Log
import nest.sparkle.loader.kafka.AvroRecordGenerators.GeneratedRecord
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.store.cassandra.CassandraStore
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.util.ConfigureLog4j
import nest.sparkle.store.cassandra.CassandraReaderWriter

class TestStreamLoadAvroKafka extends FunSuite with Matchers with PropertyChecks
    with CassandraTestConfig with Log {

  def expectedPath(id: String): String = s"sample-data/path/$id/Latency/value"
  override def testKeySpace:String = "testStreamLoadAvroKafka"
  val kafkaLoaderConfig = rootConfig.getConfig("sparkle-time-server.kafka-loader")
    ConfigureLog4j.configure(kafkaLoaderConfig)

  test("load a stream contains a few milliDouble arrays into cassandra") {
    import ExecutionContext.Implicits.global

    withTestDb{ testStore =>
      forAll(genArrayRecords(1), MinSuccessful(1)) { generatedRecords =>
        KafkaTestUtil.withTestAvroTopic(rootConfig, MillisDoubleArrayAvro.schema) { kafka =>

          // prefill kafka queue
          kafka.writer.write(generatedRecords.map { _.record })

          // run loader
          val overrides =
            "sparkle-time-server.kafka-loader.topics" -> List(kafka.topic) ::
              "sparkle-time-server.kafka-loader.auto-start" -> "false" ::
              "sparkle-time-server.kafka-loader.find-decoder" -> "nest.sparkle.loader.kafka.MillisDoubleArrayFinder" ::
              Nil

          val modifiedRoot = modifiedConfig(rootConfig, overrides: _*)
          val loader = new AvroKafkaLoader[Long](modifiedRoot, testStore)
          val watch = Watch[ColumnUpdate[Long]]()
          loader.watch(watch) // watch loading progress
          val watchBuffer = watch.report.buffer(generatedRecords.length)
          watchBuffer.subscribe() // RX is this necessary?

          loader.load()
          val futureUpdates = watchBuffer.take(1).map(_.head).toFutureSeq
          futureUpdates.await()
          futureUpdates.foreach { updates => checkWatch(updates, generatedRecords) }
          checkCassandra(testStore, generatedRecords)
          loader.close()
        }
      }
    }

    /** verify that the watch() notifications are correct */
    def checkWatch(updates: Seq[ColumnUpdate[Long]], generatedRecords: List[GeneratedRecord[Long, Double]]) {
      // verify update reports
      val generatedPaths = generatedRecords.map{ record => expectedPath(record.id) }
      updates.foreach { update =>
        generatedPaths.contains(update.columnPath) shouldBe true
      }
    }

    /** verify that the correct data is in cassandra */
    def checkCassandra(testStore:CassandraReaderWriter, generatedRecords: List[GeneratedRecord[Long, Double]]) {
      // verify matching data in cassandra
      generatedRecords.foreach { generated =>
        val column = testStore.column[Long, Double](expectedPath(generated.id)).await
        val results = column.readRange(None, None).initial.toBlocking.toList
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
