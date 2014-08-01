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

import scala.concurrent.ExecutionContext
import scala.collection.SortedSet

import spray.util._

import org.scalatest.{FunSuite, Matchers}
import org.scalatest.prop.PropertyChecks

import nest.sparkle.loader.kafka.AvroRecordGenerators.{GeneratedRecord, genArrayRecords}
import nest.sparkle.store.cassandra.{CassandraReaderWriter, CassandraTestConfig}
import nest.sparkle.util.ConfigUtil.modifiedConfig
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.util.{ConfigureLog4j, Log, Watch}

class TestStreamLoadAvroKafka extends FunSuite with Matchers with PropertyChecks
    with CassandraTestConfig with Log {
  
  import scala.concurrent.ExecutionContext.Implicits.global

  def expectedPath(id1: String, id2: String) = {
    id2 match {
      // NULL is the default value specified in MillisDoubleArrayFinder
      case null => s"sample-data/path/$id1/NULL/Latency/value"
      case _    => s"sample-data/path/$id1/$id2/Latency/value"
    }
  }
  
  override def testKeySpace:String = "testStreamLoadAvroKafka"
  
  val kafkaLoaderConfig = rootConfig.getConfig("sparkle-time-server")
    ConfigureLog4j.configureLogging(kafkaLoaderConfig)

  test("load a stream contains a few milliDouble arrays into cassandra") {
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
  }
  
  test("load a stream containing a record with a null id into C*") {
    withTestDb { testStore =>
      KafkaTestUtil.withTestAvroTopic(rootConfig, MillisDoubleArrayAvro.schema) { kafka =>
        
        val id1 = "abc"
        val id2 = null
        val events = Seq(1L -> 13.1)
        val eventsSet = SortedSet(events: _*)
        val latencyRecord = AvroRecordGenerators.makeLatencyRecord(id1, id2, events)
        val generatedRecords = List(AvroRecordGenerators.GeneratedRecord[Long,Double](id1, id2, eventsSet, latencyRecord))

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
    val generatedPaths = generatedRecords.map{ record => expectedPath(record.id1, record.id2) }
    updates.foreach { update =>
      generatedPaths.contains(update.columnPath) shouldBe true
    }
  }

  /** verify that the correct data is in cassandra */
  def checkCassandra(testStore:CassandraReaderWriter, generatedRecords: List[GeneratedRecord[Long, Double]]) {
    // verify matching data in cassandra
    generatedRecords.foreach { generated =>
      val column = testStore.column[Long, Double](expectedPath(generated.id1, generated.id2)).await
      val results = column.readRange(None, None).initial.toBlocking.toList
      generated.events.size shouldBe results.length
      generated.events zip results map {
        case ((time, dataValue), event) =>
          time shouldBe event.argument
          dataValue shouldBe event.value
      }
    }
  }

}
