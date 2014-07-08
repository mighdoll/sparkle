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

class TestIntervalSum extends FunSuite with Matchers with CassandraTestConfig with StreamRequestor {

  def withIntervalTest[T](resource: String)(fn: Seq[Event[Long, Long]] => T): T = {
    val resourceFile = resource + ".csv"
    withLoadedPath(resourceFile, resourceFile){ (store, system) =>
      val service = new ServiceWithCassandra(store, system)
      val params = IntervalParameters[Long](ranges = None, partSize = Some("1 hour"))
      val selector = SelectString(s"$resource/millis")
      val message = streamRequest("IntervalSum", params, selector)
      service.v1TypedRequest(message) { events: Seq[Seq[Event[Long, Long]]] =>
        val data = events.head
        fn(data)
      }
    }
  }
  
  test("test identifying some basic overlaps") {
    withIntervalTest("intervals-basic"){ data =>
      data(0) shouldBe Event("2014-07-05T17:00:00.000-07:00".toMillis, 1.hour.toMillis)
      data(1) shouldBe Event("2014-07-05T18:00:00.000-07:00".toMillis, 1.hour.toMillis)
      data(2) shouldBe Event("2014-07-05T19:00:00.000-07:00".toMillis, 0)
      data(3) shouldBe Event("2014-07-05T20:00:00.000-07:00".toMillis, 10.seconds.toMillis)
    }
  }
  
  test("test summing two in an hour") {
    withIntervalTest("intervals-two-per-hour"){ data =>
      data(0) shouldBe Event("2014-07-05T21:00:00.000-07:00".toMillis, 20.seconds.toMillis)
    }
  }
  
  test("test summing two in an hour, one partial") {
    withIntervalTest("intervals-two-and-partial"){ data =>
      data(0) shouldBe Event("2014-07-05T21:00:00.000-07:00".toMillis, 20.seconds.toMillis)
      data(1) shouldBe Event("2014-07-05T22:00:00.000-07:00".toMillis, 10.seconds.toMillis)
    }
  }

}

class ServiceWithCassandra(override val store: Store, actorSystem: ActorSystem) extends FunSuite with TestDataService {
  override def actorRefFactory: ActorSystem = actorSystem

} 
