package nest.sparkle.time.protocol

import spray.http.{HttpResponse, StatusCodes}
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

import nest.sparkle.test.SparkleTestConfig
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.{StatusMessageFormat, StreamsMessageFormat}
import nest.sparkle.time.protocol.TransformParametersJson.RawParametersFormat
import nest.sparkle.util.ConfigUtil.sparkleConfigName

class TestStaticAuthentication extends TestStore with SparkleTestConfig with TestDataService with StreamRequestor {
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

  test("try a request with a bad password") {
    makeRequest(Some("wrongPassword")) {
     // TODO fix logging
      log.error(s"info.logging is disabled, why? info.enabled:${log.underlying.isInfoEnabled}")
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 611
    }
  }

  test("try a request with a good password") {
    makeRequest(Some(password)) {
      responseAs[StreamsMessage] // or we'll fail
    }
  }

  test("try a request with a missing password") {
    makeRequest(None) {
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 612
    }
  }

  test("try a request with a missing realm") {
    makeRequest(None) {
      val statusMessage = responseAs[StatusMessage]
      statusMessage.message.code shouldBe 612
    }
  }

  // TODO verify that logs don't have id and auth in them

}