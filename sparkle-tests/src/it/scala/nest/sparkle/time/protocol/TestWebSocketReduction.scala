package nest.sparkle.time.protocol

import org.scalatest.{Matchers, FunSuite}
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.time.server.DataWebSocketFixture.withWebSocketRequest
import nest.sparkle.util.StringToMillis.IsoDateString
import scala.concurrent.duration._
import nest.sparkle.time.protocol.TestDataService._

class TestWebSocketReduction extends FunSuite with Matchers
    with CassandraStoreTestConfig with StreamRequestor {

  test("reduce empty period through web socket") {
    val start = "2014-12-01T01:00:00.000".toMillis
    val until = "2014-12-01T01:30:00.000".toMillis
    val message = stringRequest("simple-events/seconds", "reduceSum",
      s"""{ "ranges":[ {
         |    "start": $start,
         |    "until": $until
         |  } ],
         |  "intoDurationParts" : 2,
         |  "emitEmptyPeriods" : true
         |} """.stripMargin)

    withLoadedFile("simple-events.csv") { (store, system) =>
      implicit val actorSystem = system
      import system.dispatcher
      withWebSocketRequest(rootConfig, store, message, 1) { responses =>
        val initial = longDoubleFromStreams(responses(0))
        initial.length shouldBe 0
      }
    }
  }

}
