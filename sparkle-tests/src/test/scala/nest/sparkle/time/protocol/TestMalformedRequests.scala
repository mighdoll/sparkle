package nest.sparkle.time.protocol
import spray.http.MediaTypes.`application/json`
import spray.http.HttpHeaders.`Content-Type`
import spray.http.StatusCodes.OK

class TestMalformedRequests extends TestStore with StreamRequestor with TestDataService {
  nest.sparkle.util.InitializeReflection.init

  test("missing transform parameters on Raw transform") {
    val msg = s"""
      {
        "messageType": "StreamRequest",
        "message": {
          "sources": [
      		  "$testColumnPath"
          ],
          "transform":"raw",
          "transformParameters":{
          }
        }
      }
    """

    Post("/v1/data", msg) ~> v1protocol ~> check {
      status shouldBe OK
    }
  }

}