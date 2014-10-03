package nest.sparkle.time.protocol

import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.CassandraTestConfig
import spray.json.DefaultJsonProtocol._
import nest.sparkle.time.protocol.TransformParametersJson.IntervalParametersFormat

trait IntervalSumFixture {
  self: CassandraTestConfig with StreamRequestor =>
  def withIntervalTest( // format: OFF
      resource: String,
      partSize: Option[String] = Some("1 hour"),
      ranges: Option[Seq[RangeInterval[Long]]] = None
      )(fn: Seq[Event[Long, Seq[Long]]] => Unit): Unit = { // format: ON
    val resourceFile = resource + ".csv"
    withLoadedFile(resourceFile) { (store, system) =>
      val service = new ServiceWithCassandra(store, system)
      val params = IntervalParameters[Long](ranges = ranges, partSize = partSize)
      val selector = SelectString(s"$resource/millis")
      val message = streamRequest("IntervalSum", params, selector)
      service.v1TypedRequest(message) { events: Seq[Seq[Event[Long, Seq[Long]]]] =>
        val data = events.head
        fn(data)
      }
    }
  }

}