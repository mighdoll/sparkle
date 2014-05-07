package nest.sparkle.time.protocol

import spray.json.DefaultJsonProtocol._

import nest.sparkle.store.Event

class TestSummarizeMax extends TestStore with StreamRequestor with TestDataService {
//  implicit val routeTestTimeout = RouteTestTimeout(1.hour)

  test("summarizeMax two raw events") { // note that this test just copies input to output
    val message = streamRequest("SummarizeMax")
    v1Request(message){ events =>
      events.length shouldBe 2
      events(0).value shouldBe 1
      events(1).value shouldBe 2
    }
  }

  test("summarizeMax simple set of events to one") {
    val message = streamRequest("SummarizeMax", selector = SelectString(simpleColumnPath),
      range = RangeParameters[Long](maxResults = 1))
    v1Request(message){ events =>
      events.length shouldBe 1
      events.head shouldBe Event(simpleMidpointMillis, 32)
    }
  }

  test("summarizeMax, 3->2 events, selecting by start") {
    val message = streamRequest("SummarizeMax", SelectString(simpleColumnPath),
      RangeParameters[Long](maxResults = 2, start = Some("2013-01-19T22:13:50".toMillis)))

    v1Request(message){ events =>
      events.length shouldBe 2
      events.head shouldBe Event("2013-01-19T22:13:50".toMillis, 28)
      events.last shouldBe Event("2013-01-19T22:14:00".toMillis, 25)
    }
  }

  test("summarizeMax, selecting end") {
    val message = streamRequest("SummarizeMax", SelectString(simpleColumnPath),
      RangeParameters[Long](maxResults = 2, end = Some("2013-01-19T22:13:50".toMillis)))
    v1Request(message){ events =>
      events.length shouldBe 2
      events.head shouldBe Event("2013-01-19T22:13:20".toMillis, 26)
      events.last shouldBe Event("2013-01-19T22:13:40".toMillis, 32)
    }
  }

  test("summarizeMax, selecting start + end") {
    val message = streamRequest("SummarizeMax", SelectString(simpleColumnPath),
      RangeParameters[Long](maxResults = 3, 
          start = Some("2013-01-19T22:13:30".toMillis),
          end = Some("2013-01-19T22:14:20".toMillis)))
    v1Request(message){ events =>
      events.length shouldBe 3
      events.head shouldBe Event("2013-01-19T22:13:40".toMillis, 32)
      events.last shouldBe Event("2013-01-19T22:14:10".toMillis, 20)
    }    
  }
  
  test("summarizeMax on uneven data") {
    val message = streamRequest("SummarizeMax", SelectString(unevenColumnPath),
      RangeParameters[Long](maxResults = 2, 
          start = Some("2013-01-19T22:13:10".toMillis),
          end = Some("2013-01-19T22:13:41".toMillis)))
    v1Request(message){ events =>
      events.length shouldBe 2
      events.head shouldBe Event("2013-01-19T22:13:12".toMillis, 31)
      events.last shouldBe Event("2013-01-19T22:13:40".toMillis, 32)
    }    
  }

}
