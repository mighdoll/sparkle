package nest.sparkle.time.server

import spray.http.HttpHeaders._
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest

import akka.actor.ActorRefFactory
import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.measure.DummyMeasurements
import nest.sparkle.store.Store
import nest.sparkle.test.SparkleTestConfig
import nest.sparkle.util.ExpectHeaders

class TestDataAdminService extends FunSuite with Matchers
    with ScalatestRouteTest with DataAdminService with SparkleTestConfig with ExpectHeaders {

  override def actorSystem = system
  override def actorRefFactory: ActorRefFactory = actorSystem
  def executionContext = actorSystem.dispatcher
  val measurements = DummyMeasurements
  override def store:Store = null // we don't need a store for this test

  test("load the default admin index page from resources") {
    Get() ~> allRoutes ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.MovedPermanently
      expectHeader(`Location`) { _ == "/admin/index.html"}
    }
  }

}
