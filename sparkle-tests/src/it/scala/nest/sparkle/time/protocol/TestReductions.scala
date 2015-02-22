package nest.sparkle.time.protocol

import scala.concurrent.duration._

import spray.http.HttpResponse

import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.time.protocol.TestDataService.longDoubleData
import nest.sparkle.util.FutureAwait.Implicits._
import nest.sparkle.util.StringToMillis.IsoDateString

class TestReductions extends FunSuite with Matchers
    with CassandraStoreTestConfig with StreamRequestor {

  def requestWithLoaded
      ( fileOrDirectory:String, request:String )
      ( fn: HttpResponse => Unit ):Unit = {
    withLoadedFile(fileOrDirectory) { (store, system) =>
      val service = new TestServiceWithCassandra(store, system)
      val response = service.sendDataMessage(request).await
      fn(response)
    }
  }

  test("sum a few elements with no requested range and no requested period") {
    val message = stringRequest("simple-events/seconds", "reduceSum")
    requestWithLoaded("simple-events.csv", message) { response =>
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
    val message = stringRequest("simple-events/seconds", "reduceSum",
      """{ "partBySize" : "1 hour" } """)
    requestWithLoaded("simple-events.csv", message) { response =>
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
    val start = "2014-12-01T01:00:00.000Z".toMillis
    val until = "2014-12-01T04:00:00.000Z".toMillis
    val message = stringRequest("simple-events/seconds", "reduceSum",
      s"""{ "partBySize" : "1 hour",
           |  "ranges": [ {
           |    "start": $start,
           |    "until": $until
           |   } ]
           |} """.stripMargin)
    requestWithLoaded("simple-events.csv", message) { response =>
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

  test("sum a few elements with a start = data and end < data, 1 hour period") {
    val start = "2014-12-01T00:00:00.000Z".toMillis
    val until = "2014-12-01T02:00:00.000Z".toMillis
    val message = stringRequest("simple-events/seconds", "reduceSum",
      s"""{ "partBySize" : "1 hour",
           |  "ranges": [ {
           |    "start": $start,
           |    "until": $until
           |   } ]
           |} """.stripMargin)
    requestWithLoaded("simple-events.csv", message) { response =>
      val data = longDoubleData(response)
      val keys = data.map { case (key, _) => key}
      val values = data.map { case (_, value) => value}
      keys shouldBe Seq(
        "2014-12-01T00:00:00.000Z".toMillis,
        "2014-12-01T01:00:00.000Z".toMillis
      )
      values shouldBe Seq(
        Some(8),
        None
      )
    }
  }


  test("sum a few elements with a specified start and no period") {
    val start = "2014-12-01T01:11:00.000Z".toMillis
    val until = "2014-12-01T04:00:00.000Z".toMillis
    val message = stringRequest("simple-events/seconds", "reduceSum",
      s"""{ "ranges": [ {
           |    "start": $start,
           |    "until": $until
           |   } ]
           |} """.stripMargin)
    requestWithLoaded("simple-events.csv", message) { response =>
      val data = longDoubleData(response)
      data.length shouldBe 1
      data.head match {
        case (key, value) =>
          key shouldBe "2014-12-01T01:11:00.000Z".toMillis
          value shouldBe Some(2)
      }
    }
  }

  def testSimpleByCount(count:Int, expectedKeys:Seq[String], expectedValues:Seq[Option[Double]]): Unit = {
    val message = stringRequest("simple-events/seconds", "reduceSum",
      s"""{ "partByCount" : $count } """)
    requestWithLoaded("simple-events.csv", message) { response =>
      val data = longDoubleData(response)
      data.length shouldBe expectedKeys.length
      val keys = data.map { case (key, _) => key}
      val values = data.map { case (_, value) => value}

      val expectedMillis = expectedKeys.map(_.toMillis)

      keys shouldBe expectedMillis
      values shouldBe expectedValues
    }
  }


  test("sum five elements, count = 6") {
    testSimpleByCount(
      count = 6,
      expectedKeys = Seq("2014-12-01T00:00:00.000"),
      expectedValues = Seq(Some(10))
    )
  }

  test("sum five elements, count = 5") {
    testSimpleByCount(
      count = 5,
      expectedKeys = Seq("2014-12-01T00:00:00.000"),
      expectedValues = Seq(Some(10))
    )
  }

  test("sum five elements, count = 2") {
    testSimpleByCount(
      count = 2,
      expectedKeys = Seq(
        "2014-12-01T00:00:00.000",
        "2014-12-01T00:40:00.000",
        "2014-12-01T02:00:00.000"
      ),
      expectedValues = Seq(
        Some(4),
        Some(4),
        Some(2)
      )
    )
  }

  test("intoCountedParts, 2 parts") {
        val message = stringRequest("simple-events/seconds", "reduceSum",
      """{ "intoCountedParts" : 2 } """)
    requestWithLoaded("simple-events.csv", message) { response =>
      val data = longDoubleData(response)
      data.length shouldBe 2
      data shouldBe Seq(
        "2014-12-01T00:00:00.000".toMillis -> Some(6),
        "2014-12-01T00:50:00.000".toMillis -> Some(4)
      )
    }
  }


}
