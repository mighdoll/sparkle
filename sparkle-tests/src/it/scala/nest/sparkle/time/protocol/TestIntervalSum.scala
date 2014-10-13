package nest.sparkle.time.protocol

import scala.concurrent.duration.DurationInt
import org.scalatest.{ FunSuite, Matchers }
import akka.actor.ActorSystem
import akka.util.Timeout.durationToTimeout
import nest.sparkle.store.{ Event, Store }
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.util.StringToMillis.IsoDateString
import spray.http.StatusCodes.OK
import spray.json.DefaultJsonProtocol._
import spray.util.pimpFuture
import nest.sparkle.time.transform.IntervalSum
import nest.sparkle.store.cassandra.TestServiceWithCassandra

class TestIntervalSum extends FunSuite with Matchers with CassandraTestConfig with StreamRequestor with IntervalSumFixture {
 
  test("test identifying some basic overlaps") {
    withIntervalTest("intervals-basic") { data =>
      data(0) shouldBe Event("2014-07-06T00:00:00.000Z".toMillis, Seq(1.hour.toMillis))
      data(1) shouldBe Event("2014-07-06T01:00:00.000Z".toMillis, Seq(1.hour.toMillis))
      data(2) shouldBe Event("2014-07-06T02:00:00.000Z".toMillis, Seq(0))
      data(3) shouldBe Event("2014-07-06T03:00:00.000Z".toMillis, Seq(10.seconds.toMillis))
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

  test("test two overlapping") {
    withIntervalTest("intervals-overlap") { data =>
      data(0) shouldBe Event("2014-07-06T00:00:00.000Z".toMillis, Seq(1.hour.toMillis))
      data(1) shouldBe Event("2014-07-06T01:00:00.000Z".toMillis, Seq(1.hour.toMillis))
    }
  }

  test("test summing by minute") {
    withIntervalTest("intervals-short", Some("1 minute")) { data =>
      data(0) shouldBe Event("2014-07-05T17:00:00.000-07:00".toMillis, Seq(1.minute.toMillis))
      data(1) shouldBe Event("2014-07-05T17:01:00.000-07:00".toMillis, Seq(1.minute.toMillis))
      data(2) shouldBe Event("2014-07-05T17:02:00.000-07:00".toMillis, Seq(1.minute.toMillis))
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
      }"""
      val service = new TestServiceWithCassandra(store, system)
      val response = service.sendDataMessage(msg).await(4.seconds)
      response.status shouldBe OK
      val data = TestDataService.dataFromStreamsResponse[Seq[Long]](response).head.head // remove the type parameter to crash the compiler (2.10.4)! see SI-8824
      val expectedTime = "2014-07-06T00:00:00.000Z".toMillis // first start time
      val expectedValue = 2.hours.toMillis + 10.seconds.toMillis // total intervals
      data shouldBe Event(expectedTime, Seq(expectedValue))
    }
  }

  test("partSize 1 millisecond only returns max 3000 points") {
    withIntervalTest("intervals-basic", Some("1 millisecond")) { data =>
      data.length shouldBe 3000
    }
  }
  
  test("partSize 0 returns 0 points") {
    withIntervalTest("intervals-basic", Some("0 days")) { data =>
      data.length shouldBe 0
    }
  }
  
  test("no partSize, with overlap") {
    withIntervalTest("intervals-overlap", None) { data =>
      data.length shouldBe 1
      data(0).value shouldBe Seq(2.hours.toMillis)
    }
  }

  // TODO verify combined results with missing values result in an aligned array with a missing values (not a mis-aligned array!)

}
