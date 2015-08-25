package nest.sparkle.time.protocol

import spray.json.DefaultJsonProtocol._
import nest.sparkle.time.protocol.TransformParametersJson.RawParametersFormat

class TestRawTransform extends PreloadedRamService with StreamRequestor {

  test("Raw transform two raw events") {
    val message = streamRequest("Raw", params = RawParameters[Long]())
    v1TypicalRequest(message){ events =>
      val events = TestDataService.typicalStreamData(response)
      events.length shouldBe 2
      events(0).value shouldBe 1
      events(1).value shouldBe 2
    }
  }

  test("raw simple range") {
    val range = RangeInterval(
      start = Some("2013-01-19T22:13:30Z".toMillis),
      until = Some("2013-01-19T22:14:00Z".toMillis)
    )

    val params = RawParameters[Long](ranges = Some(Seq(range)))
    val message = streamRequest("Raw", params = params, selector = SelectString(simpleColumnPath))
    v1TypicalRequest(message) { events =>
      events.length shouldBe 3
      events(0).key shouldBe range.start.get
      events(2).key shouldBe "2013-01-19T22:13:50Z".toMillis
    }
  }

}