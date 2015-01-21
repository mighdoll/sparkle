package nest.sparkle.time.protocol

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.util.FutureAwait.Implicits._
import scala.concurrent.duration._
import nest.sparkle.time.protocol.TestDataService.longDoubleData
import nest.sparkle.util.StringToMillis.IsoDateString

class TestReductions extends FunSuite with Matchers with CassandraStoreTestConfig with StreamRequestor {
  def request(transform: String, transformParameters: String = "{}"): String = {
    s"""{
    |  "messageType": "StreamRequest",
    |  "message": {
    |    "sources": [
  	|  	  "simple-events/seconds"
    |    ],
    |    "transform": "$transform",
    |    "transformParameters": $transformParameters
    |  }
    |}""".stripMargin
  }

  test("sum a few elements with no requested range and no requested period") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val service = new TestServiceWithCassandra(store, system)
      val message = request("reduceSum")
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

  // TODO fixme, fails intermittently
  test("sum a few elements with no requested range with a 1 hour period") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      log.info("TestReductions claims file loaded (1 hour period test)")
      val service = new TestServiceWithCassandra(store, system)
      val message = request("reduceSum", """{ "partBySize" : "1 hour" } """)
      val response = service.sendDataMessage(message, 1.hour).await

      val data = longDoubleData(response)
      data.length shouldBe 3
      val keys = data.map { case (key, _) => key }
      val values = data.map { case (_, value) => value }
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
}
