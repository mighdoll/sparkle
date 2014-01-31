/* Copyright 2013  Nest Labs

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

import scala.concurrent.ExecutionContext
import org.scalatest.{ BeforeAndAfterAll, FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks
import spray.util._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport._
import spray.testkit.ScalatestRouteTest
import nest.sparkle.store.Event
import nest.sparkle.store.Storage
import nest.sparkle.time.protocol.TransformParametersJson.SummarizeParamsFormat
import nest.sparkle.time.protocol.RequestJson.{ StreamRequestMessageFormat }
import nest.sparkle.time.protocol.ResponseJson.{ StreamFormat, StreamsFormat, StreamsMessageFormat }
import nest.sparkle.time.protocol.EventJson.EventFormat
import nest.sparkle.store.ram.WriteableRamStore
import scala.reflect.runtime.universe._
import spray.http.HttpResponse
import nest.sparkle.time.transform.DomainRangeLimits
import nest.sparkle.time.transform.DomainRangeJson.DomainRangeReader
import nest.sparkle.time.transform.MinMax

class TestV1Api extends FunSuite with Matchers with ScalatestRouteTest with DataServiceV1
    with PropertyChecks with BeforeAndAfterAll {
  def executionContext = ExecutionContext.global
  def store: Storage = testDb

  lazy val testColumn = "latency.p99"
  lazy val testId = "server1"
  lazy val testColumnPath = s"$testId/$testColumn"

  // for now we test against the cassandra store, LATER use ram based storage when we port over the other stuff
  lazy val testDb = {
    val store = new WriteableRamStore()
    val column = store.writeableColumn[Long, Double](testColumnPath).await
    column.create("a test column").await

    val writeColumn = store.writeableColumn[Long, Double](testColumnPath).await
    writeColumn.write(Seq(Event(100L, 1), Event(200L, 2))).await

    store
  }

  var currentRequestId = 0
  /** return a request id and trace id for a new protocol request */
  def requestIds(): (Int, String) = synchronized {
    currentRequestId = currentRequestId + 1
    (currentRequestId, "trace-" + currentRequestId.toString)
  }

  /** return a new StreamRequestMessage */
  def streamRequest(transform: String, maxResults: Int = 10): StreamRequestMessage = {
    val summarizeParams = SummarizeParams[Long](maxResults = maxResults)

    val (requestId, traceId) = requestIds()
    val paramsJson = summarizeParams.toJson(SummarizeParamsFormat[Long](LongJsonFormat)).asJsObject // SCALA (spray-json) can this be less explicit?

    val sources = Array(testColumnPath.toJson)
    val streamRequest = StreamRequest(sendUpdates = None, itemLimit = None, sources = sources, transform = transform, paramsJson)

    StreamRequestMessage(requestId = Some(requestId),
      realm = None, traceId = Some(traceId), messageType = "StreamRequest", message = streamRequest)
  }

  /** return the data array portion from a Streams response */
  def streamData(response: HttpResponse): Seq[JsArray] = {
    val streams = responseAs[StreamsMessage]
    streams.message.streams.length shouldBe 1
    val stream = streams.message.streams(0)
    stream.data.isDefined shouldBe true
    val data = stream.data.get
    data
  }

  /** return the data array portion from a Streams response as a stream of Event objects */
  def streamDataEvents(response: HttpResponse): Seq[Event[Long, Double]] = {
    val data = streamData(response)
    data.map { datum =>
      datum.convertTo[Event[Long, Double]]
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

  import nest.sparkle.time.protocol.ArbitraryColumn.arbitraryColumn
  test("DomainRange calculates range") {
    val requestMessage = streamRequest("DomainRange")  
    val x = arbitraryColumn("V1Protocol.domainRange", testDb)   // COMPILER bug, remove this to fix
    
    Post("/v1/data", requestMessage) ~> v1protocol ~> check {
      val data = streamData(response)
      data.length shouldBe 1
      data.foreach { datum =>
        val limits = datum.convertTo[DomainRangeLimits[Long, Double]]
        limits shouldBe DomainRangeLimits(domain = MinMax(100L, 200L), range = MinMax(1,2))
      }
    }
  }

}