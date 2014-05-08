package nest.sparkle.time.protocol

import spray.json.DefaultJsonProtocol._

class TestRawTransform extends TestStore with StreamRequestor with TestDataService {

  test("Raw transform two raw events") {
    val message = streamRequest[Long]("Raw")
    v1Request(message){ events =>
      val events = streamDataEvents(response)
      events.length shouldBe 2
      events(0).value shouldBe 1
      events(1).value shouldBe 2
    }
  }

  test("raw simple range") {
    val start = Some("2013-01-19T22:13:30Z".toMillis)
    val end = Some("2013-01-19T22:14:00Z".toMillis)
    val range = RangeParameters[Long](maxResults = 100, start = start, end = end)
    val message = streamRequest("Raw", selector = SelectString(simpleColumnPath), range = range)
    v1Request(message) { events =>
      events.length shouldBe 3
      events(0).argument shouldBe start.get
      events(2).argument shouldBe "2013-01-19T22:13:50Z".toMillis
    }
  }

}