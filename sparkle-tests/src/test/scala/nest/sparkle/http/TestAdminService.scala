package nest.sparkle.http

import org.scalatest.{FunSuite, Matchers}
import spray.testkit.ScalatestRouteTest

import akka.actor.ActorRefFactory

import nest.sparkle.test.SparkleTestConfig

class TestAdminService
  extends FunSuite
    with Matchers
    with AdminService
    with ScalatestRouteTest
    with SparkleTestConfig
{
  /** Set actor reference */
  override def actorRefFactory: ActorRefFactory = system
  def executionContext = system.dispatcher
  
  /** Test that body is the default page */
  protected def defaultPage(value: String) = {
    value.startsWith("<!DOCTYPE html>") && value.contains("Default admin page!")
  }
  
  // this suite will be moved the http common problem to avoid resource conflicts on classpath
  ignore("load the default index page from resources") {
    //implicit val ref = actorRefFactory
    Get() ~> routes ~> check {
      handled shouldBe true
      defaultPage(body.asString) shouldBe true
    }
  }
}
