package nest.sparkle.time.protocol

import java.util.concurrent.TimeUnit._
import scala.collection.immutable.IndexedSeq
import scala.concurrent.{Promise, Future, ExecutionContext}
import scala.concurrent.duration.FiniteDuration
import scala.util.Success
import com.typesafe.config.Config
import spray.http.HttpResponse
import spray.testkit.ScalatestRouteTest

import akka.actor.{ActorRefFactory, ActorSystem}
import org.scalatest
import org.scalatest._

import nest.sparkle.measure.{Measurements, ConfiguredMeasurements}
import nest.sparkle.store.{Store, ReadWriteStore}
import nest.sparkle.time.server.DataService
import nest.sparkle.util.ConfigUtil._

trait DataServiceFixture extends DataService with ScalatestRouteTest {
  self: Suite =>
  def readWriteStore: ReadWriteStore

  lazy val defaultTimeout = {
    val protocolConfig = configForSparkle(rootConfig).getConfig("protocol-tests")
    val millis = protocolConfig.getDuration("default-timeout", MILLISECONDS)
    FiniteDuration(millis, MILLISECONDS)
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

object DataServiceFixture {
  def withDataServiceFixture[T]
      ( theRootConfig:Config, theReadWriteStore: ReadWriteStore )
      ( fn:DataServiceFixture => T )
      ( implicit theActorSystem:ActorSystem, theMeasurements: Measurements )
      : T = {
    val testService =
      new FunSuite with DataServiceFixture {
        override implicit def executionContext: ExecutionContext = actorSystem.dispatcher
        override def actorSystem: ActorSystem = theActorSystem
        override implicit def actorRefFactory: ActorRefFactory = actorSystem
        override def measurements: Measurements = theMeasurements
        override def readWriteStore: ReadWriteStore = theReadWriteStore
        override def store: Store = readWriteStore
        override def rootConfig: Config = theRootConfig
//        override val system = theActorSystem // TODO doesn't work, so we have two ActorSystems around
    }

    try {
      fn(testService)
    } finally {
      testService.cleanUp()
    }

  }
}