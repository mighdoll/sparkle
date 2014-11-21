package nest.sparkle.http

import com.typesafe.config.ConfigFactory

import org.scalatest.{FunSuite, Matchers}
import spray.http.StatusCodes._
import spray.testkit.ScalatestRouteTest

import akka.actor.ActorRefFactory

import nest.sparkle.measure.DummyMeasurements

class TestAdminService
  extends FunSuite
    with Matchers
    with ScalatestRouteTest
    with AdminService
{
  /** Set actor reference */
  override def actorRefFactory: ActorRefFactory = system
  def executionContext = system.dispatcher
  val measurements = DummyMeasurements
  
  /** We'd like to use SparkleTestConfig but it's in the test-kit project
    * which includes the test dependencies in the main config which we don't want to depend on.
    */
  val rootConfig =  ConfigFactory.load()
  
  /** Test that body is the default page */
  protected def defaultPage(value: String) = {
    value.startsWith("<!DOCTYPE html>") && value.contains("Default admin page!")
  }
  
  test("load the default admin index page from resources") {
    Get() ~> allRoutes ~> check {
      handled shouldBe true
      status shouldBe OK
      defaultPage(body.asString) shouldBe true
    }
  }
  
  test("load the health page") {
    Get("/health") ~> allRoutes ~> check {
      handled shouldBe true
      status shouldBe OK
      body.asString shouldBe "ok"
    }
  }
}
