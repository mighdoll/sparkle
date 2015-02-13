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
  def withEventsAndMore[T]
      ( transformParameters:String = "{}",
        transform:String = "reduceSum",
        loadCount:Int = 2 )
      ( fn: Seq[String] => T): T = {
    var receivedCount = 0
    val received = Vector.newBuilder[String]
    val finished = Promise[Unit]()

    withLoadedFile("simple-events.csv") { (store, system) =>
      implicit val actorSystem = system
      import system.dispatcher
      withDataWebSocket(rootConfig, store) { port =>
        val message = stringRequest("simple-events/seconds", transform, transformParameters)
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
        finished.future.await(45.seconds)
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



  /** run with a fixed set of period parameters and data. Each tests varies the reduction performed */
  def ongoingNoRangeOneHourTest
      ( transform:String,
        expectedInitialValues:Seq[Option[Double]],
        expectedOngoingValues:Seq[Option[Double]]): Unit = {

    val transformParams =
      """ { "partBySize" : "1 hour",
        |   "ongoingBufferPeriod": "3 seconds"
          }
      """.stripMargin

    val expectedInitialStringKeys = Seq(
      "2014-12-01T00:00:00.000",
      "2014-12-01T01:00:00.000",
      "2014-12-01T02:00:00.000"
    )
    val expectedOngoingStringKeys = Seq(
      "2014-12-01T02:00:00.000",
      "2014-12-01T03:00:00.000",
      "2014-12-01T04:00:00.000"
    )
    val expectedInitialKeys = expectedInitialStringKeys.map(_.toMillis)
    val expectedOngoingKeys = expectedOngoingStringKeys.map(_.toMillis)


    withEventsAndMore(transformParams, transform) { responses =>
      val initial = longDoubleFromStreams(responses(0))
      initial.length shouldBe 3
      val (initialKeys, initialValues) = initial.unzip
      initialKeys shouldBe expectedInitialKeys
      initialValues shouldBe expectedInitialValues

      val ongoing = longDoubleFromUpdate(responses(1))
      ongoing.length shouldBe 3
      val (ongoingKeys, ongoingValues) = ongoing.unzip
      ongoingKeys shouldBe expectedOngoingKeys
      ongoingValues shouldBe expectedOngoingValues
    }

  }



  test("sum with ongoing, no requested range, with 1 hour period") {
    ongoingNoRangeOneHourTest("reduceSum",
      Seq(Some(8),None, Some(2)),
      Seq(Some(7), None, Some(5))
    )
  }

  test("mean with ongoing, no requested range, with 1 hour period") {
    ongoingNoRangeOneHourTest("reduceMean",
      Seq(Some(2),None,Some(2)),
      Seq(Some(3.5),None,Some(5))
    )
  }

  test("min with ongoing, no requested range, with 1 hour period") {
    ongoingNoRangeOneHourTest("reduceMin",
      Seq(Some(1), None, Some(2)),
      Seq(Some(2), None, Some(5))
    )
  }

  test("max with ongoing, no requested range, with 1 hour period") {
    ongoingNoRangeOneHourTest("reduceMax",
      Seq(Some(3),None,Some(2)),
      Seq(Some(5),None,Some(5))
    )
  }


}
