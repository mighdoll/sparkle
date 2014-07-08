package nest.sparkle.time.protocol

import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.StatusMessageFormat
import nest.sparkle.time.protocol.ResponseJson.StreamsMessageFormat
import nest.sparkle.time.protocol.TransformParametersJson.RawParametersFormat
import spray.http.StatusCodes
import spray.http.HttpResponse

class TestStaticAuthentication extends TestStore with TestDataService with StreamRequestor {
  def password = "foo" // def for initialization order issues
  override def configOverrides: List[(String, Any)] = super.configOverrides ++ List(
      "sparkle-time-server.auth.password" -> password,
      "sparkle-time-server.auth.provider" -> classOf[StaticAuthentication].getCanonicalName().toString
    )

  def makeRequest[T](passwordOpt: Option[String] = None)(fn: HttpResponse => T): T = {
    val realm = Realm("sparkle", Some("myId"), passwordOpt)
    makeRequestWithRealm(Some(realm)){ fn }
  }

  def makeRequestWithRealm[T](realmOpt: Option[Realm])(fn: HttpResponse => T): T = {
    val baseMessage = streamRequest("Raw", params = RawParameters[Long]())
    val message = baseMessage.copy(realm = realmOpt)

    Post("/v1/data", message) ~> v1protocol ~> check {
      response.status shouldBe StatusCodes.OK
      fn(response)
    }
  }

  test("try a request with a bad password") {
    makeRequest(Some("wrongPassword")) { response =>
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 611
    }
  }

  test("try a request with a good password") {
    makeRequest(Some(password)) { response =>
      responseAs[StreamsMessage] // or we'll fail
    }
  }

  test("try a request with a missing password") {
    makeRequest(None) { response =>
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 612
    }
  }

  test("try a request with a missing realm") {
    makeRequest(None) { response =>
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 612
    }
  }

}