package nest.sparkle.time.protocol

import nest.sparkle.util.{LogUtil, MetricsInstrumentation}
import org.scalatest.{Matchers, FunSuite}
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.time.server.DataWebSocketFixture.withWebSocketRequest
import nest.sparkle.util.StringToMillis.IsoDateString
import scala.concurrent.duration._
import nest.sparkle.time.protocol.TestDataService._
import scala.collection.JavaConverters._

class TestWebSocketReduction extends FunSuite with Matchers
    with CassandraStoreTestConfig with StreamRequestor {

  override def testConfigFile: Option[String] = Some("tests")

  def simpleReduce(columnName:String)(fn: Seq[String]=>Unit) {
    val start = "2014-12-01T01:00:00.000".toMillis
    val until = "2014-12-01T01:30:00.000".toMillis
    val message = stringRequest("simple-events/" + columnName, "reduceSum",
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
        fn(responses)
      }
    }
  }

  def verifyTimerExists(timerName:String): Unit = {
    val timers = MetricsInstrumentation.registry.getTimers.asScala
    val timer = timers(timerName)
    timer.getCount >= 1 shouldBe true

  }

  test("reduce empty period through web socket") {
    simpleReduce("seconds") { responses =>
      val initial = longDoubleFromStreams(responses(0))
      initial.length shouldBe 0
    }
  }

  test("verify metric reporting through web socket") {
    simpleReduce("seconds"){ _ =>
      verifyTimerExists("data-request-websocket")
    }
  }

  test("verify metric reporting errors through web socket") {
    LogUtil.withLogLevel(ProtocolError.getClass, "ERROR") {
      simpleReduce("not-there"){ _ =>
        verifyTimerExists("data-request-websocket-error")
      }
    }
  }

}
