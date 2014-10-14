package nest.sparkle.http

import org.scalatest.{FunSuite, Matchers}
import spray.http.StatusCodes._
import spray.testkit.ScalatestRouteTest

import akka.actor.ActorRefFactory

abstract class TestStaticContent
  extends FunSuite
    with Matchers
    with StaticContent
    with ScalatestRouteTest
{
  /** Set actor reference */
  override def actorRefFactory: ActorRefFactory = system
  
  /** Test that body is the default page */
  protected def defaultPage(value: String) = {
    value.startsWith("<!DOCTYPE html>") && value.contains("Default common static page!")
  }
}

class TestStaticContentBuiltIn extends TestStaticContent {
  test("load the default index page from resources") {
    Get() ~> staticContent ~> check {
      handled shouldBe true
      defaultPage(body.asString) shouldBe true
    }
  }
}

abstract class TestStaticContentCustomFolder extends TestStaticContent {
  test("should load a file that exists") {
    Get("/test.txt") ~> staticContent ~> check {
      handled shouldBe true
      status shouldBe OK
      body.asString shouldBe "test file"
    }
  }
  
  test("no leading slash should be okay") {
    Get("test.txt") ~> staticContent ~> check {
      handled shouldBe true
      status shouldBe OK
      body.asString shouldBe "test file"
    }
  }
  
  test("non-existant file should not be handled") {
    Get("/aaaaaaaaa.html") ~> staticContent ~> check {
      handled shouldBe false
    }
  }
  
  test("missing index.html should not be handled") {
    Get() ~> staticContent ~> check {
      handled shouldBe false
    }
  }
}

class TestStaticContentFileFolder extends TestStaticContentCustomFolder {
  /** Location w/files */
  override def webRoot: Option[FileOrResourceLocation] = 
    Some(FileLocation("http-common/src/test/resources/test"))
}

class TestStaticContentResourceFolder extends TestStaticContentCustomFolder {
  /** Location w/files */
  override def webRoot: Option[FileOrResourceLocation] = 
    Some(ResourceLocation("test"))
}
