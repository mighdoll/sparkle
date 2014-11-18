package nest.sparkle.time.protocol

import spray.http.{ HttpResponse, StatusCodes }
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

import nest.sparkle.test.SparkleTestConfig
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.{ StatusMessageFormat, StreamsMessageFormat }
import nest.sparkle.time.protocol.TransformParametersJson.RawParametersFormat
import nest.sparkle.util.ConfigUtil.sparkleConfigName

class TestStaticAuthentication extends PreloadedRamStore with SparkleTestConfig with TestDataService with StreamRequestor {
  def password = "foo" // def for initialization order issues
  override def configOverrides: List[(String, Any)] = super.configOverrides ++ List(
    s"$sparkleConfigName.auth.password" -> password,
    s"$sparkleConfigName.auth.provider" -> classOf[StaticAuthentication].getCanonicalName
  )

  def makeRequest[T](passwordOpt: Option[String] = None)(fn: => T): T = {
    val realm = RealmToServer("sparkle", Some("myId"), passwordOpt)
    makeRequestWithRealm(Some(realm)) { fn }
  }

  def makeRequestWithRealm[T](realmOpt: Option[RealmToServer])(fn: => T): T = {
    val baseMessage = streamRequest("Raw", params = RawParameters[Long]())
    val message = baseMessage.copy(realm = realmOpt)

    Post("/v1/data", message) ~> route ~> check {
      response.status shouldBe StatusCodes.OK
      fn
    }
  }

  test("bad password should be rejected") {
    makeRequest(Some("wrongPassword")) {
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 611
    }
  }

  test("good password should be accepted") {
    makeRequest(Some(password)) {
      responseAs[StreamsMessage] // or we'll fail
    }
  }

  test("missing password should be rejected") {
    makeRequest(None) {
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 612
    }
  }

  test("missing realm should be rejected") {
    makeRequest(None) {
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 612
    }
  }

  test("unauthorized columnNotFound should be rejected for auth (not columnNotFound)") {
    val msg ="""
      {
        "realm": {
          "name": "sparkle",
          "id": "1234"
        },
        "messageType": "StreamRequest",
        "message": {
          "sources": [
      		  "not/really/there"
          ],
          "transform":"raw", 
				  "transformParameters": {}
        }
      }"""

    Post("/v1/data", msg) ~> route ~> check {
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 612
    }
  }

}