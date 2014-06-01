package nest.sparkle.time.protocol
import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.Event

class TestSummarizeSum extends TestStore with StreamRequestor with TestDataService {
  nest.sparkle.util.InitializeReflection.init
  
  test("summarizeSum simple set to one") {
    val message = summaryRequestOne[Long]("SummarizeSum", selector = SelectString(simpleColumnPath))

    val sum = simpleEvents.map{case Event(k,v) => v}.sum
    v1Request(message){ events =>
      events.length shouldBe 1
      events.head shouldBe Event("2013-01-19T22:13:40Z".toMillis, sum)
    }
  }
  test("summarizeSum uneven set to one") {
    val message = summaryRequestOne[Long]("SummarizeSum", selector = SelectString(unevenColumnPath))

    val sum = unevenEvents.map{case Event(k,v) => v}.sum
    val sumTime = unevenEvents.map{case Event(k,v) => k}.sum
    v1Request(message){ events =>
      events.length shouldBe 1
      events.head shouldBe Event(sumTime / unevenEvents.length, sum)
    }
  }
  
}