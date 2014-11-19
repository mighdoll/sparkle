package nest.sparkle.http

import com.typesafe.config.{Config, ConfigFactory}

import org.scalatest.{FunSuite, Matchers}

import akka.actor.ActorRefFactory

import spray.http.StatusCodes._
import spray.http.ContentTypes._
import spray.testkit.ScalatestRouteTest
import spray.json._

class TestDisplayConfig
  extends FunSuite
    with Matchers
    with ScalatestRouteTest
    with DisplayConfig
{
  /** Set actor reference */
  override def actorRefFactory: ActorRefFactory = system
  def executionContext = system.dispatcher
  
  lazy val rootConfig: Config = ConfigFactory.load()
  
  test("config is correct") {
    Get("/config") ~> configRoutes ~> check {
      handled shouldBe true
      status shouldBe OK
      contentType shouldBe `application/json`
      val json = body.asString.asJson.asJsObject
      json.fields("sparkle-time-server") shouldBe a [JsObject]
      val sparkleConfig = json.fields("sparkle-time-server").asJsObject
      sparkleConfig.fields("logging") shouldBe a [JsObject]
    }
  }
  
  test("config/commented is correct") {
    Get("/config/commented") ~> configRoutes ~> check {
      handled shouldBe true
      status shouldBe OK
      contentType shouldBe `text/plain(UTF-8)`
      val text = body.asString
      text should startWith ("{\n")
      text should endWith ("}\n")
      text should include ("sparkle-time-server")
    }
  }
}
