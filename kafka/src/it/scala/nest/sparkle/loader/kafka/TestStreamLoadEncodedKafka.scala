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

import scala.collection.SortedSet
import scala.reflect.runtime.universe._

import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.prop.{ PropertyChecks, TableDrivenPropertyChecks }

import nest.sparkle.loader.ColumnUpdate
import nest.sparkle.store.cassandra.{ CassandraReaderWriter, CassandraTestConfig }
import nest.sparkle.util.Log
import nest.sparkle.util.ConfigUtil.{modifiedConfig, sparkleConfigName, configForSparkle}

import nest.sparkle.loader.kafka.MillisDoubleTSVGenerators._

import spray.util.pimpFuture

class TestStreamLoadEncodedKafka extends FunSuite with Matchers with PropertyChecks with TableDrivenPropertyChecks
    with KafkaTestConfig with CassandraTestConfig with Log {

  import scala.concurrent.ExecutionContext.Implicits.global

  def expectedPath(id1: String, id2: String) = {
    id2 match {
      // NULL is the default value specified in MillisDoubleArrayFinder
      case null => s"sample-data/path/$id1/${MillisDoubleTSVFinder.id2Default}/Latency/value"
      case _    => s"sample-data/path/$id1/$id2/Latency/value"
    }
  }

  override def testKeySpace: String = "testStreamLoadAvroKafka"

  private val kafkaLoaderConfig = {
    val sparkleConfig = configForSparkle(rootConfig)
    sparkleConfig.getConfig("kafka-loader")
  }

  def writeAndVerify(records: Seq[MillisDoubleTSV], columnPath: String) {
    withTestDb { testStore =>
      KafkaTestUtil.withTestEncodedTopic(rootConfig, MillisDoubleTSVSerde) { kafka =>

        // prefill kafka queue
        kafka.writer.write(records)
        // run loader
        val overrides =
          s"$sparkleConfigName.kafka-loader.topics" -> List(kafka.topic) ::
          s"$sparkleConfigName.kafka-loader.find-decoder" -> "nest.sparkle.loader.kafka.MillisDoubleTSVFinder" ::
          Nil

        val storeWrite = testStore.writeListener.listen[Long](columnPath)
        val modifiedRoot = modifiedConfig(rootConfig, overrides: _*)
        val loader = new KafkaLoader[Long](modifiedRoot, testStore)
        loader.start()

        try {
          storeWrite.take(records.length).toBlocking.head // await completion
          checkCassandra(testStore, records)
        } finally {
          log.debug("shutting down loader")
          loader.shutdown()
        }
      }
    }
  }

  val sampleRecords = Table(
    ("id1", "id2", "key", "value"),
    ("foo", "bar", 1L, 1.0)
  )

  test("load some sample MillisDouble records") {
    forAll(sampleRecords) { (id1, id2, key, value) =>
      val record = MillisDoubleTSV(id1, id2, key, value)
      val columnPath = expectedPath(id1, id2)
      writeAndVerify(Seq(record), columnPath)
    }
  }

  test("load a stream containing generated milliDouble arrays") {
    forAll(genRecords(1), MinSuccessful(1)) { generatedRecords =>
      val columnPath = expectedPath(generatedRecords.head.id1, generatedRecords.head.id2)
      writeAndVerify(generatedRecords, columnPath)
    }
  }

  /** verify that the watch() notifications are correct */
  def checkWatch(updates: Seq[ColumnUpdate[Long]], generatedRecords: Seq[MillisDoubleTSV]) {
    // verify update reports
    val generatedPaths = generatedRecords.map { record => expectedPath(record.id1, record.id2) }
    updates.foreach { update =>
      generatedPaths.contains(update.columnPath) shouldBe true
    }
  }

  /** verify that the correct data is in cassandra */
  def checkCassandra(testStore: CassandraReaderWriter, generatedRecords: Seq[MillisDoubleTSV]) {
    // verify matching data in cassandra
    generatedRecords.foreach { generated =>
      val column = testStore.column[Long, Double](expectedPath(generated.id1, generated.id2)).await
      val results = column.readRange(None, None).initial.toBlocking.toList
      results.length shouldBe 1 //is it possible that the same id1/id2 combos was randomly generated more than once?
      generated.key shouldBe results(0).argument
      generated.value shouldBe results(0).value
    }
  }

}
