package nest.sparkle.time.protocol

import scala.concurrent.Promise
import scala.util.Success

import org.scalatest.{Matchers, FunSuite}
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.measure.ConfiguredMeasurements
import nest.sparkle.test.PortsTestFixture.sparklePortsConfig
import nest.sparkle.time.protocol.TestDataService._
import nest.sparkle.time.server.DataWebSocket
import nest.sparkle.util.ConfigUtil.modifiedConfig
import nest.sparkle.util.FutureAwait.Implicits._
import nest.sparkle.util.StringToMillis.IsoDateString
import nest.sparkle.time.server.DataWebSocketFixture.withDataWebSocket
import scala.concurrent.duration._

class TestOngoingReductions extends FunSuite with Matchers
    with CassandraStoreTestConfig with StreamRequestor {

  /** Start a test websocket server, load some simple event data, send a reduction
    * request, and then load some more data.
    * Returns the response messages received over the protocol.
    * @param transformParameters stringified json object for transformParameters
    * @param loadCount number of test data files to load
    * @param fn function to run after all loadCount messages have been received
    * @tparam T type of the returned data values
    * @return the json string messages over the protocol
    */
  def withEventsAndMore[T](transformParameters:String = "{}", loadCount:Int = 2)
                          (fn: Seq[String] => T):T = {
    var receivedCount = 0
    val received = Vector.newBuilder[String]
    val finished = Promise[Unit]()

    withLoadedFile("simple-events.csv") { (store, system) =>
      implicit val actorSystem = system
      import system.dispatcher
      withDataWebSocket(rootConfig, store) { port =>
        val message = stringRequest("simple-events/seconds", "reduceSum", transformParameters)
        tubesocks.Sock.uri(s"ws://localhost:$port/data") {
          case tubesocks.Open(s) =>
            s.send(message)
          case tubesocks.Message(text, socket) =>
            received += text
            receivedCount += 1
            receivedCount match  {
              case n if n < loadCount  => withLoadedResource(s"_more-events-$receivedCount/simple-events.csv", store) {}
              case n if n == loadCount => finished.complete(Success(Unit))
              case n if n > loadCount  => ??? // shouldn't happen
            }
        }
        finished.future.await(15.seconds)
        fn(received.result)
      }
    }
  }

  test("sum with ongoing, no requested range, no requested period") {
    val transformParams = """{ "ongoingBufferPeriod": "3 seconds" }"""

    withEventsAndMore(transformParams) { responses =>
      val initial = longDoubleFromStreams(responses(0))
      initial.length shouldBe 1
      initial.head match {
        case (key, value) =>
          key shouldBe "2014-12-01T00:00:00.000Z".toMillis
          value shouldBe Some(10)
      }
      val update = longDoubleFromUpdate(responses(1))
      update.length shouldBe 1
      update.head match {
        case (key, value) =>
          key shouldBe "2014-12-01T02:10:00.000Z".toMillis
          value shouldBe Some(10)
      }
    }
  }


  test("sum with ongoing, no requested range, with 1 hour period") {
    val transformParams =
      """ { "partBySize" : "1 hour",
        |   "ongoingBufferPeriod": "3 seconds"
          }
      """.stripMargin
    withEventsAndMore(transformParams) { responses =>
      val initial = longDoubleFromStreams(responses(0))
      initial.length shouldBe 3
      val keys = initial.map { case (key, _) => key}
      val values = initial.map { case (_, value) => value}
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

      val ongoing = longDoubleFromUpdate(responses(1))
      val ongoingKeys = ongoing.map { case (key, _) => key}
      val ongoingValues = ongoing.map { case (_, value) => value}
      ongoing.length shouldBe 3

      ongoingKeys shouldBe Seq(
        "2014-12-01T02:00:00.000Z".toMillis,
        "2014-12-01T03:00:00.000Z".toMillis,
        "2014-12-01T04:00:00.000Z".toMillis
      )

      ongoingValues shouldBe Seq(
        Some(7),
        None,
        Some(5)
      )
    }
  }

}
