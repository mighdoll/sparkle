package nest.sparkle.time.protocol

import scala.reflect.runtime.universe._
import scala.reflect.runtime.universe.TypeTag.{Double, Long}

import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.util._
import spray.json._

import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.ArbitraryColumn.arbitraryEvent
import nest.sparkle.time.protocol.TestDomainRange.minMaxEvents
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.transform.{DomainRange, MinMax}
import nest.sparkle.time.transform.DomainRangeJson
import nest.sparkle.time.transform.DomainRangeJson.DomainRangeFormat
import nest.sparkle.util.RandomUtil.randomAlphaNum

class TestDomainRangeRequest extends PreloadedRamStore with StreamRequestor with TestDataService {
  nest.sparkle.util.InitializeReflection.init

  /** create a new column in the test RAM store and return its columnPath */
  def makeColumn[T: TypeTag, U: TypeTag](prefix: String, events: List[Event[T, U]]): String = {
    val columnName = prefix + "/" + randomAlphaNum(4)
    val column = store.writeableColumn[T, U](columnName).await
    column.write(events)
    columnName
  }

  test("DomainRange calculates domain and range on arbitrary long,double columns") {
    forAll { events: List[Event[Long, Double]] =>
      val columnName = makeColumn("V1Protocol.DomainRange", events)
      val requestMessage = streamRequest("DomainRange", JsObject(), SelectString(columnName))
      Post("/v1/data", requestMessage) ~> v1protocol ~> check {
        val data = TestDataService.streamDataJson(response).head
        data.length shouldBe 2
        if (events.isEmpty) {
          data shouldBe DomainRangeJson.Empty
        } else {
          val domainRange = data.toJson.convertTo[DomainRange[Long, Double]]
          val (domainMin, domainMax, rangeMin, rangeMax) = minMaxEvents(events)
          domainRange shouldBe DomainRange(domain = MinMax(domainMin, domainMax), range = MinMax(rangeMin, rangeMax))
        }
      }
    }
  }
}