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

import scala.concurrent.duration._
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
import nest.sparkle.util.{InitializeReflection, ConfigUtil}
import spray.routing.RoutingSettings
import spray.http.DateTime
import spray.http.HttpResponse
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Success

trait TestDataService extends DataService with ScalatestRouteTest with SparkleTestConfig with Matchers {
  self: Suite =>

  override lazy val corsHosts = List("*")
  override def actorSystem = system
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  def executionContext = system.dispatcher

  // tell spray test config about our configuration (and trigger logging initialization)
  override def testConfig = ConfigUtil.configForSparkle(rootConfig)

  // TODO legacy, delete soon
  def registry: DataRegistry = ???

  /** make a stream request, expecting a single stream of long/double results */
  def v1TypicalRequest(message: StreamRequestMessage)(fn: Seq[Event[Long, Double]] => Unit) {
    v1TypedRequest[Double](message) { seqEvents =>
      fn(seqEvents.head)
    }
  }

  /** make a stream request, and report all stream data returned as events */
  def v1TypedRequest[U: JsonFormat](message: StreamRequestMessage)(fn: Seq[Seq[Event[Long, U]]] => Unit) {
    //     uncomment when debugging
    //    implicit val timeout: RouteTestTimeout = {
    //      println("setting timeout to 1 hour for debugging")
    //      RouteTestTimeout(1.hour)
    //    }
    Post("/v1/data", message) ~> v1protocol ~> check {
      val events = TestDataService.dataFromStreamsResponse[U](response)
      fn(events)
    }
  }

  /** send a json string to the data port and report back the http response */
  def sendDataMessage(message: String):Future[HttpResponse] = {
    val promised = Promise[HttpResponse]
    Post("/v1/data", message) ~> v1protocol ~> check { 
      log.debug(s"got response: $response}")
      promised.complete(Success(response))
    }
    promised.future
  }

}

object TestDataService {
  /** return the data array portion from a Streams response as a sequence of Event objects */
  def typicalStreamData(response: HttpResponse): Seq[Event[Long, Double]] = {
    dataFromStreamsResponse[Double](response).head
  }

  /** return the data array portions from a Streams response, each as a sequence of Event objects */
  def dataFromStreamsResponse[U: JsonFormat](response: HttpResponse): Seq[Seq[Event[Long, U]]] = {
    streamDataJson(response).map { data =>
      data.map(_.convertTo[Event[Long, U]])
    }
  }

  case class StreamsResponseError(msg: String) extends RuntimeException(msg)

  /** return all the data from the streams in a Streams message */
  def streamDataJson(response: HttpResponse): Seq[Seq[JsArray]] = {
    import spray.httpx.unmarshalling._
    val streamsEither = response.as[StreamsMessage]
    streamsEither.left.map { err =>
      throw StreamsResponseError(err.toString)
    }

    val streams = streamsEither.right.get
    assert(streams.message.streams.length > 0)
    val datas =
      streams.message.streams.map { stream =>
        stream.data.get
      }
    datas
  }

  def printMillisEvents[T](events: Seq[Event[Long, T]]) {
    val eventText = events.map { event =>
      val dateTime = DateTime(event.argument)
      val dateString = dateTime.toString
      Event(dateString, event.value)
    }
    println(s"events: $eventText")
  }

}