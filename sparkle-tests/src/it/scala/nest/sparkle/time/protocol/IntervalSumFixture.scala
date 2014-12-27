package nest.sparkle.time.protocol

import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.CassandraTestConfig
import spray.json.DefaultJsonProtocol._
import nest.sparkle.time.protocol.TransformParametersJson.IntervalParametersFormat

trait IntervalSumFixture {
  self: CassandraTestConfig with StreamRequestor =>

  /** make an IntervalSum query against a store loaded with data in a .csv test file */
  def withIntervalTest( // format: OFF
      resource: String,
      partBySize: Option[String] = Some("1 hour"),
      ranges: Option[Seq[RangeInterval[Long]]] = None,
      selector:Option[TestSelector] = None
      )(fn: Seq[Event[Long, Seq[Long]]] => Unit): Unit = { // format: ON
    
    val resourceFile = resource + ".csv"
    withLoadedFile(resourceFile) { (store, system) =>
      val service = new TestServiceWithCassandra(store, system)
      val params = IntervalParameters[Long](ranges = ranges, partBySize = partBySize, None)
      val select = selector.getOrElse(SelectString(s"$resource/millis"))
      val message = streamRequest("IntervalSum", params, select)
      service.v1TypedRequest(message) { events: Seq[Seq[Event[Long, Seq[Long]]]] =>
        val data = events.head
        fn(data)
      }
    }
  }

}