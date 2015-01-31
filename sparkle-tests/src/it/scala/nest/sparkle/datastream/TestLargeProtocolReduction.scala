package nest.sparkle.datastream

import scala.concurrent.duration._

import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.datastream.LargeReduction.generateDataStream
import nest.sparkle.measure.DummySpan
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.time.protocol.TestDataService.longLongData
import nest.sparkle.time.protocol.{TestServiceWithCassandra, StreamRequestor}
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.FutureAwait.Implicits._

class TestLargeProtocolReduction extends FunSuite with Matchers
    with CassandraStoreTestConfig with StreamRequestor {

  test("try a large reduction test end to end, including cassandra and protocol") {
    implicit val span = DummySpan
    val stream = generateDataStream(1.hour)
    val columnPath = "test/data"
    val message = stringRequest(columnPath, "reduceSum",
      """{ "partBySize" : "1 day",
        |  "timeZoneId" : "UTC" }""".stripMargin)

    withTestDb { store =>
      withTestActors { implicit system =>
        implicit val execution = store.execution
        store.writeStream(stream, columnPath).await

        val service = new TestServiceWithCassandra(store, system)
        val response = service.sendDataMessage(message).await
        val reduced = longLongData(response)
        reduced.length shouldBe 365
        reduced.foreach { case (key, value) => value shouldBe Some(48)}
      }
    }
  }
}
