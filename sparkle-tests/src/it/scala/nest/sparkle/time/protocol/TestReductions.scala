package nest.sparkle.time.protocol

import scala.concurrent.duration._

import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.time.protocol.TestDataService.longDoubleData
import nest.sparkle.util.FutureAwait.Implicits._
import nest.sparkle.util.StringToMillis.IsoDateString

class TestReductions extends FunSuite with Matchers
    with CassandraStoreTestConfig with StreamRequestor {

  test("sum a few elements with no requested range and no requested period") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val service = new TestServiceWithCassandra(store, system)
      val message = stringRequest("simple-events/seconds", "reduceSum")
      val response = service.sendDataMessage(message, 1.hour).await
      val data = longDoubleData(response)
      data.length shouldBe 1
      data.head match {
        case (key, value) =>
          key shouldBe "2014-12-01T00:00:00.000Z".toMillis
          value shouldBe Some(10)
      }
    }
  }

  test("sum a few elements with no requested range with a 1 hour period") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val service = new TestServiceWithCassandra(store, system)
      val message = stringRequest("simple-events/seconds", "reduceSum",
        """{ "partBySize" : "1 hour" } """)
      val response = service.sendDataMessage(message, 1.hour).await

      val data = longDoubleData(response)
      data.length shouldBe 3
      val keys = data.map { case (key, _) => key}
      val values = data.map { case (_, value) => value}
      keys shouldBe Seq(
        "2014-12-01T00:00:00.000Z".toMillis,
        "2014-12-01T01:00:00.000Z".toMillis,
        "2014-12-01T02:00:00.000Z".toMillis
      )
      values shouldBe Seq(
        Some(8),
        None,
        Some(2)
      )
    }
  }

  test("sum a few elements with a specified start and end > data, 1 hour period") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val service = new TestServiceWithCassandra(store, system)
      val start = "2014-12-01T01:00:00.000Z".toMillis
      val until = "2014-12-01T04:00:00.000Z".toMillis
      val message = stringRequest("simple-events/seconds", "reduceSum",
        s"""{ "partBySize" : "1 hour",
           |  "ranges": [ {
           |    "start": $start,
           |    "until": $until
           |   } ]
           |} """.stripMargin)
      val response = service.sendDataMessage(message, 1.hour).await

      val data = longDoubleData(response)
      val keys = data.map { case (key, _) => key}
      val values = data.map { case (_, value) => value}
      keys shouldBe Seq(
        "2014-12-01T01:00:00.000Z".toMillis,
        "2014-12-01T02:00:00.000Z".toMillis,
        "2014-12-01T03:00:00.000Z".toMillis
      )
      values shouldBe Seq(
        None,
        Some(2),
        None
      )

    }
  }

  test("sum a few elements with a specified start and no period") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val service = new TestServiceWithCassandra(store, system)
      val start = "2014-12-01T01:11:00.000Z".toMillis
      val until = "2014-12-01T04:00:00.000Z".toMillis
      val message = stringRequest("simple-events/seconds", "reduceSum",
        s"""{ "ranges": [ {
           |    "start": $start,
           |    "until": $until
           |   } ]
           |} """.stripMargin)
      val response = service.sendDataMessage(message, 1.hour).await

      val data = longDoubleData(response)
      data.length shouldBe 1
      data.head match {
        case (key, value) =>
          key shouldBe "2014-12-01T01:11:00.000Z".toMillis
          value shouldBe Some(2)
      }
    }
  }

}
