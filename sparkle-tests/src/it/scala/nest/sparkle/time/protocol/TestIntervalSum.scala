package nest.sparkle.time.protocol

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.store.Store
import nest.sparkle.time.protocol.TransformParametersJson.IntervalParametersFormat
import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.Event
import akka.actor.ActorSystem
import nest.sparkle.util.StringToMillis._
import scala.concurrent.duration._
import spray.http.StatusCodes.OK
import spray.util._

class TestIntervalSum extends FunSuite with Matchers with CassandraTestConfig with StreamRequestor {

  def withIntervalTest(resource: String, partSize: String = "1 hour") // format: OFF
      (fn: Seq[Event[Long, Seq[Long]]] => Unit): Unit = { // format: ON
    val resourceFile = resource + ".csv"
    withLoadedFile(resourceFile) { (store, system) =>
      val service = new ServiceWithCassandra(store, system)
      val params = IntervalParameters[Long](ranges = None, partSize = Some(partSize))
      val selector = SelectString(s"$resource/millis")
      val message = streamRequest("IntervalSum", params, selector)
      service.v1TypedRequest(message) { events: Seq[Seq[Event[Long, Seq[Long]]]] =>
        val data = events.head
        fn(data)
      }
    }
  }

  test("test identifying some basic overlaps") {
    withIntervalTest("intervals-basic") { data =>
      data(0) shouldBe Event("2014-07-05T17:00:00.000-07:00".toMillis, Seq(1.hour.toMillis))
      data(1) shouldBe Event("2014-07-05T18:00:00.000-07:00".toMillis, Seq(1.hour.toMillis))
      data(2) shouldBe Event("2014-07-05T19:00:00.000-07:00".toMillis, Seq(0))
      data(3) shouldBe Event("2014-07-05T20:00:00.000-07:00".toMillis, Seq(10.seconds.toMillis))
    }
  }

  test("test summing two in an hour") {
    withIntervalTest("intervals-two-per-hour") { data =>
      data(0) shouldBe Event("2014-07-05T21:00:00.000-07:00".toMillis, Seq(20.seconds.toMillis))
    }
  }

  test("test summing two in an hour, one partial") {
    withIntervalTest("intervals-two-and-partial") { data =>
      data(0) shouldBe Event("2014-07-05T21:00:00.000-07:00".toMillis, Seq(20.seconds.toMillis))
      data(1) shouldBe Event("2014-07-05T22:00:00.000-07:00".toMillis, Seq(10.seconds.toMillis))
    }
  }

  test("test summing by minute") {
    withIntervalTest("intervals-short", "1 minute") { data =>
      data(0) shouldBe Event("2014-07-05T17:00:00.000-07:00".toMillis, Seq(1.minute.toMillis))
      data(1) shouldBe Event("2014-07-05T17:01:00.000-07:00".toMillis, Seq(1.minute.toMillis))
      data(2) shouldBe Event("2014-07-05T17:02:00.000-07:00".toMillis, Seq(1.minute.toMillis))
      // TODO generates an extra empty event. Should be fixed
    }
  }

  test("missing transform parameters on IntervalSum transform") {
    withLoadedFile("intervals-basic.csv") { (store, system) =>
      val msg = s"""{
        "messageType": "StreamRequest",
        "message": {
          "sources": [
      		  "intervals-basic/millis"
          ],
          "transform": "IntervalSum",
          "transformParameters": {
          }
        }
      }
     """
      val service = new ServiceWithCassandra(store, system)
      val response = service.sendDataMessage(msg).await(4.seconds)
      response.status shouldBe OK
      val data = TestDataService.dataFromStreamsResponse[Seq[Long]](response).head.head       // remove the type parameter to crash the compiler (2.10.4)! see SI-8824
      val expectedTime = "2014-07-05T18:30:00.000-07:00".toMillis // mean of start times
      val expectedValue = 2.hours.toMillis + 10.seconds.toMillis  // total intervals
      data shouldBe Event(expectedTime, Seq(expectedValue))
    }
  }

  // TODO verify combined results with missing values result in an aligned array with a missing values (not a mis-aligned array!)

}

/** separate instance of test service, so we can create it within a withLoadedPath block */
class ServiceWithCassandra(override val store: Store, actorSystem: ActorSystem) extends FunSuite with TestDataService {
  override def actorRefFactory: ActorSystem = actorSystem
} 
