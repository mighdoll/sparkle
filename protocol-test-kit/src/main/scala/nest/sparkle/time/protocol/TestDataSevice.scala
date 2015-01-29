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

import java.util.concurrent.TimeUnit.MILLISECONDS

import scala.concurrent.{Future, Promise}
import scala.util.Success
import scala.concurrent.duration._

import org.scalatest.{BeforeAndAfterAll, FunSuite, Suite}

import akka.actor.ActorSystem
import spray.http.{DateTime, HttpResponse}
import spray.httpx._
import spray.httpx.unmarshalling._
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport._
import spray.testkit.ScalatestRouteTest
import nest.sparkle.measure.ConfiguredMeasurements
import nest.sparkle.store.{Event, Store}
import nest.sparkle.test.SparkleTestConfig
import nest.sparkle.time.protocol.EventJson.EventFormat
import nest.sparkle.time.server.DataService
import nest.sparkle.util.ConfigUtil.configForSparkle
import nest.sparkle.time.protocol.ResponseJson.StreamsMessageFormat
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat

trait TestDataService extends DataService with ScalatestRouteTest with SparkleTestConfig {
  self: Suite with BeforeAndAfterAll =>

  override lazy val measurements = new ConfiguredMeasurements(rootConfig)
  override def actorSystem = system
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  def executionContext = system.dispatcher

  // tell spray test config about our configuration (and trigger logging initialization)
  override def testConfig = configForSparkle(rootConfig)

  def close(): Unit = measurements.close()

  override def afterAll(): Unit = {
    super.afterAll()
    measurements.close()
  }

  lazy val defaultTimeout = {
    val protocolConfig = configForSparkle(rootConfig).getConfig("protocol-tests")
    val millis = protocolConfig.getDuration("default-timeout", MILLISECONDS)
    FiniteDuration(millis, MILLISECONDS)
  }

  /** make a stream request, expecting a single stream of long/double results */
  def v1TypicalRequest(message: StreamRequestMessage)(fn: Seq[Event[Long, Double]] => Unit) {
    v1TypedRequest[Double](message) { seqEvents =>
      fn(seqEvents.head)
    }
  }

  /** make a stream request, and report all stream data returned as events */
  def v1TypedRequest[U: JsonFormat] // format: OFF
      (message: StreamRequestMessage, timeout: FiniteDuration = defaultTimeout)
      (fn: Seq[Seq[Event[Long, U]]] => Unit) {
    implicit val routeTimeout: RouteTestTimeout = RouteTestTimeout(timeout)
    Post("/v1/data", message) ~> v1protocol ~> check {
      val events = TestDataService.dataFromStreamsResponse[U](response)
      fn(events)
    }
  }

  /** send a json string to the data port and report back the http response */
  def sendDataMessage(message: String, timeout: FiniteDuration = defaultTimeout): Future[HttpResponse] = {
    implicit val routeTimeout: RouteTestTimeout = RouteTestTimeout(timeout)
    val promised = Promise[HttpResponse]
    Post("/v1/data", message) ~> v1protocol ~> check {
      promised.complete(Success(response))
    }
    promised.future
  }
}

object TestDataService {

  class TestDataServiceInstance(override val store: Store, actorSystem: ActorSystem)
      extends FunSuite with TestDataService {
    override def actorRefFactory: ActorSystem = actorSystem
  }

  def apply(store: Store, actorSystem: ActorSystem) = new TestDataServiceInstance(store, actorSystem)

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
  
  def longDoubleData(response:HttpResponse):Seq[(Long,Option[Double])] = {
    singleArrayFromStreamsResponse[Long,Option[Double]](response)
  }
  
  def singleArrayFromStreamsResponse[K:JsonFormat, V:JsonFormat] // format: OFF
      (response: HttpResponse)
      : Seq[(K,V)] = {
    val jsData = streamDataJson(response)
    for {
      seqArray <- jsData.headOption.toVector
      jsArray <- seqArray
    } yield {
      jsArray.convertTo[(K,V)]
    }
  }

  case class StreamsResponseError(msg: String) extends RuntimeException(msg)

  /** return all the data from the streams in a Streams message */
  def streamDataJson(response: HttpResponse): Seq[Seq[JsArray]] = {
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