package nest.sparkle.time.protocol
import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.Event

class TestSummarizeSum extends TestStore with StreamRequestor with TestDataService {
  test("summarizeSum simple set to one") {
    val message = streamRequest("SummarizeSum", selector = SelectString(simpleColumnPath),
      range = RangeParameters[Long](maxResults = 1))

    val sum = simpleEvents.map{case Event(k,v) => v}.sum
    v1Request(message){ events =>
      events.length shouldBe 1
      events.head shouldBe Event("2013-01-19T22:13:40Z".toMillis, sum)
    }
  }
  test("summarizeSum uneven set to one") {
    val message = streamRequest("SummarizeSum", selector = SelectString(unevenColumnPath),
      range = RangeParameters[Long](maxResults = 1))

    val sum = unevenEvents.map{case Event(k,v) => v}.sum
    val sumTime = unevenEvents.map{case Event(k,v) => k}.sum
    v1Request(message){ events =>
      events.length shouldBe 1
      events.head shouldBe Event(sumTime / unevenEvents.length, sum)
    }
  }
  
}