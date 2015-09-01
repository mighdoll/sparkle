package nest.sparkle.http

import com.typesafe.config.ConfigFactory

import spray.http.StatusCodes

import org.scalatest.{FunSuite, Matchers}
import spray.http.StatusCodes._
import spray.testkit.ScalatestRouteTest

import akka.actor.ActorRefFactory

import nest.sparkle.measure.DummyMeasurements
import nest.sparkle.test.SparkleTestConfig

class TestBaseAdminService
  extends FunSuite
    with Matchers
    with ScalatestRouteTest
    with BaseAdminService
    with SparkleTestConfig
{
  /** Set actor reference */
  override def actorRefFactory: ActorRefFactory = system
  def executionContext = system.dispatcher
  val measurements = DummyMeasurements
//
//  /** We'd like to use SparkleTestConfig but it's in the test-kit project
//    * which includes the test dependencies in the main config which we don't want to depend on.
//    */
//  val rootConfig =  ConfigFactory.load()


  test("load the health page") {
    Get("/health") ~> allRoutes ~> check {
      handled shouldBe true
      status shouldBe OK
      body.asString shouldBe "ok"
    }
  }
}
