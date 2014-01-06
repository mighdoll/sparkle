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

package nest.sparkle.store.cassandra

import scala.concurrent.ExecutionContext

import org.scalatest.{ BeforeAndAfterAll, FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks

import spray.util._
import spray.json._
import spray.httpx.SprayJsonSupport._
import spray.testkit.ScalatestRouteTest

import nest.sparkle.graph.ResponseJson.SingleStreamResponseFormat
import nest.sparkle.graph.DataProtocolJson.DataRequestFormat
import nest.sparkle.graph.{ DataRequest, DataServiceV1, Event }
import nest.sparkle.graph.{ SingleStreamResponse, Storage, SummarizeParams }
import nest.sparkle.graph.ParametersJson.SummarizeParamsFormat
import nest.sparkle.graph.ResponseJson.LongJsonFormat
import nest.sparkle.store.cassandra.serializers.{ DoubleSerializer, MilliTimeSerializer }

class TestV1Api extends FunSuite with Matchers with ScalatestRouteTest with DataServiceV1
    with PropertyChecks with BeforeAndAfterAll {
  def executionContext = ExecutionContext.global
  def store: Storage = testDb

  lazy val testColumn = "latency.p99"
  lazy val testId = "server1"
  lazy val columnPath = s"$testId/$testColumn"

  // for now we test against the cassandra store, LATER use ram based storage when we port over the other stuff
  lazy val testDb = {
    val store = CassandraStore("localhost")
    store.formatLocalDb("testV1events")
    val column = store.writeableColumn[MilliTime, Double](columnPath).await
    column.create("a test column").await

    val writeColumn = store.writeableColumn[MilliTime, Double](columnPath).await
    writeColumn.write(Seq(Event(MilliTime(100), 1.1d))).await

    store
  }

  test("summarizeMax") {
    val summarizeParams = SummarizeParams[Long](
      dataSet = testId,
      column = testColumn,
      maxResults = 10
    )

    val paramsJson = summarizeParams.toJson(SummarizeParamsFormat[Long](LongJsonFormat)).asJsObject // SCALA (spray-json) can this be less explicit?
    val summarizeMax = DataRequest("summarizeMax", paramsJson)

    Post("/v1/data", summarizeMax) ~> v1protocol ~> check {
      val responseStream = responseAs[SingleStreamResponse]
      println(s"results: $responseStream")
      responseStream.stream.data.length shouldBe 1
    }
  }

}