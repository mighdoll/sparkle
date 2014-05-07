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

import org.scalatest.Suite
import spray.testkit.ScalatestRouteTest
import nest.sparkle.time.server.DataService
import nest.sparkle.legacy.DataRegistry
import com.typesafe.config.ConfigFactory
import spray.http.HttpResponse
import spray.json._
import nest.sparkle.store.Event
import nest.sparkle.time.protocol.EventJson.EventFormat
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import nest.sparkle.time.protocol.ResponseJson.{ StreamsMessageFormat }
import org.scalatest.Matchers
import nest.sparkle.test.SparkleTestConfig
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat

trait TestDataService extends DataService with ScalatestRouteTest with SparkleTestConfig with Matchers {
  self: Suite =>
  override lazy val corsHosts = List("*")
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  def executionContext = system.dispatcher

  // tell spray test config about our configuration (and trigger logging initialization)
  override def testConfig = rootConfig.getConfig("sparkle-time-server")

  // TODO legacy, delete soon
  def registry: DataRegistry = ???
  
  
  def v1Request[T](message:StreamRequestMessage)(fn: Seq[Event[Long,Double]]=> T):T = {
    Post("/v1/data", message) ~> v1protocol ~> check {
      val events = TestDataService.streamDataEvents(response)
      fn(events)
    }
  }

}


object TestDataService {
  
  /** return the data array portion from a Streams response as a stream of Event objects */
  def streamDataEvents(response: HttpResponse): Seq[Event[Long, Double]] = {
    val data = streamData(response)
    data.map { datum =>
      datum.convertTo[Event[Long, Double]]
    }
  }
  
  /** return the data array portion from a Streams response.
    * 
    * Expects that there will be exactly one nonempty data stream in response.
    */  
  def streamData(response: HttpResponse): Seq[JsArray] = {
    import spray.httpx.unmarshalling._
    val streams = response.as[StreamsMessage].right.get

    assert(streams.message.streams.length == 1)
    val stream = streams.message.streams(0)
    assert(stream.data.isDefined)
    val data = stream.data.get
    data
  }

}