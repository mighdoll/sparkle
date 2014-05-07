package nest.sparkle.time.protocol

import scala.reflect.runtime.universe.TypeTag.{Double, Long}

import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.util._

import nest.sparkle.store.Event
import nest.sparkle.time.protocol.ArbitraryColumn.arbitraryEvent
import nest.sparkle.time.protocol.TestDomainRange.minMaxEvents
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.transform.{DomainRangeLimits, MinMax}
import nest.sparkle.time.transform.DomainRangeJson
import nest.sparkle.time.transform.DomainRangeJson.DomainRangeReader
import nest.sparkle.util.RandomUtil.randomAlphaNum

import scala.reflect.runtime.universe._

class TestDomainRangeRequest extends TestStore with StreamRequestor with TestDataService {

  /** create a new column in the test RAM store and return its columnPath */
  def makeColumn[T: TypeTag, U: TypeTag](prefix: String, events: List[Event[T, U]]): String = {
    val columnName = prefix + "/" + randomAlphaNum(4)
    val column = store.writeableColumn[T, U](columnName).await
    column.write(events)
    columnName
  }

  test("DomainRange calculates domain and range on arbitray long,double columns") {
    forAll { events: List[Event[Long, Double]] =>
      val columnName = makeColumn("V1Protocol.DomainRange", events)
      val requestMessage = streamRequest("DomainRange", SelectString(columnName))
      Post("/v1/data", requestMessage) ~> v1protocol ~> check {
        val data = TestDataService.streamData(response)
        data.length shouldBe 1
        data.foreach { datum =>
          if (events.length > 0) {
            val limits = datum.convertTo[DomainRangeLimits[Long, Double]]
            val (domainMin, domainMax, rangeMin, rangeMax) = minMaxEvents(events)
            limits shouldBe DomainRangeLimits(domain = MinMax(domainMin, domainMax), range = MinMax(rangeMin, rangeMax))
          } else {
            datum shouldBe DomainRangeJson.EmptyJson
          }
        }
      }
    }
  }
}