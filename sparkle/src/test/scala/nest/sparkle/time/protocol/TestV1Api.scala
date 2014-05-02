/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.time.protocol

import scala.reflect.runtime.universe._
import scala.concurrent.ExecutionContext
import spray.util._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.http.HttpResponse
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.testkit.ScalatestRouteTest
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.{ StreamFormat, StreamsFormat, StreamsMessageFormat }
import nest.sparkle.time.protocol.ArbitraryColumn.arbitraryColumn
import nest.sparkle.time.protocol.TestDomainRange.minMaxEvents
import nest.sparkle.time.protocol.ArbitraryColumn.arbitraryEvent
import nest.sparkle.time.transform.DomainRangeJson.DomainRangeReader
import nest.sparkle.time.transform.MinMax
import nest.sparkle.time.transform.DomainRangeLimits
import nest.sparkle.time.transform.DomainRangeJson
import nest.sparkle.store.Event
import nest.sparkle.store.Store
import nest.sparkle.store.ram.WriteableRamStore
import nest.sparkle.util.RandomUtil.randomAlphaNum
import spray.http.DateTime
import nest.sparkle.store.Event

class TestV1Api extends TestStore with StreamRequestor with TestDataService {

  test("Raw transform two raw events") {
    val requestMessage = streamRequest[Long]("Raw")
    Post("/v1/data", requestMessage) ~> v1protocol ~> check {
      val events = streamDataEvents(response)
      events.length shouldBe 2
      events(0).value shouldBe 1
      events(1).value shouldBe 2
    }
  }

  test("summarizeMax two raw events") { // note that this test just copies input to output
    val requestMessage = streamRequest("SummarizeMax")
    Post("/v1/data", requestMessage) ~> v1protocol ~> check {
      val events = streamDataEvents(response)
      events.length shouldBe 2
      events(0).value shouldBe 1
      events(1).value shouldBe 2
    }
  }
  
  test("summarizeMax simple set of events to one") { 
    val requestMessage = streamRequest("SummarizeMax", selector = SelectString(simpleColumnPath), 
        range = RangeParameters[Long](maxResults=1))
    Post("/v1/data", requestMessage) ~> v1protocol ~> check {
      val events = streamDataEvents(response)
      events.length shouldBe 1
      events.head shouldBe Event(simpleMidpointMillis, 32)
    }
  }
  
  test("raw simple range") { 
    val start = Some("2013-01-19T22:13:30".toMillis)
    val end = Some("2013-01-19T22:14:00".toMillis)
    val range = RangeParameters[Long](maxResults = 100, start = start, end=end)
    val requestMessage = streamRequest("Raw", selector = SelectString(simpleColumnPath), range = range)
    Post("/v1/data", requestMessage) ~> v1protocol ~> check {
      val events = streamDataEvents(response)
      events.length shouldBe 3
      events(0).argument shouldBe start.get
      events(2).argument shouldBe "2013-01-19T22:13:50".toMillis
    }
  }

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
        val data = streamData(response)
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

  test("List columns for known dataset") {
    val path = s"/v1/columns/$testId"
    Get(path) ~> v1protocol ~> check {
      val columns = responseAs[Seq[String]]
      columns.length shouldBe 2
      columns should contain(testColumnName)
    }
  }

  test("Non existant dataset should get a 404") {
    val path = s"/v1/columns/noexist"
    Get(path) ~> v1protocol ~> check {
      response.status shouldBe StatusCodes.NotFound
    }
  }

  // cors is causing a 405 instead of a 404.
  ignore("Missing dataset should get a 404") {
    val path = s"/v1/columns"
    Get(path) ~> sealRoute(v1protocol) ~> check {
      response.status shouldBe StatusCodes.NotFound
    }
  }

}
