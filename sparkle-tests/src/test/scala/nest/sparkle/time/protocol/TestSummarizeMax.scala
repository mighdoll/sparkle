package nest.sparkle.time.protocol

import spray.json.DefaultJsonProtocol._

import nest.sparkle.store.Event

class TestSummarizeMax extends PreloadedRamService with StreamRequestor {
  nest.sparkle.util.InitializeReflection.init

  test("summarizeMax two raw events") { // note that this test just copies input to output
    val message = summaryRequest[Long]("SummarizeMax", params = SummaryParameters(partByCount = Some(2)))
    v1TypicalRequest(message){ events =>
      events.length shouldBe 2
      events(0).value shouldBe 1
      events(1).value shouldBe 2
    }
  }

  test("summarizeMax simple set of events to one") {
    val message = summaryRequestOne[Long]("SummarizeMax", selector = SelectString(simpleColumnPath))
    v1TypicalRequest(message){ events =>
      events.length shouldBe 1
      events.head shouldBe Event(simpleMidpointMillis, 32)
    }
  }

  test("summarizeMax, 3->2 events, selecting by start") {
    val range = RangeInterval(start = Some("2013-01-19T22:13:50Z".toMillis))
    val message = summaryRequest("SummarizeMax", SelectString(simpleColumnPath),
      SummaryParameters[Long](partByCount = Some(2), ranges = Some(Seq(range))))

    v1TypicalRequest(message){ events =>
      events.length shouldBe 2
      events.head shouldBe Event("2013-01-19T22:13:50Z".toMillis, 28)
      events.last shouldBe Event("2013-01-19T22:14:00Z".toMillis, 25)
    }
  }

  test("summarizeMax, selecting end") {
    val range = RangeInterval(until = Some("2013-01-19T22:13:50Z".toMillis))
    val message = summaryRequest[Long]("SummarizeMax", SelectString(simpleColumnPath),
      SummaryParameters[Long](partByCount = Some(2), ranges = Some(Seq(range))))
    v1TypicalRequest(message){ events =>      
      events.length shouldBe 2
      events.head shouldBe Event("2013-01-19T22:13:20Z".toMillis, 26)
      events.last shouldBe Event("2013-01-19T22:13:40Z".toMillis, 32)
    }
  }

  test("summarizeMax, selecting start + end") {
    val range = RangeInterval(
      start = Some("2013-01-19T22:13:30Z".toMillis),
      until = Some("2013-01-19T22:14:20Z".toMillis))
    val message = summaryRequest("SummarizeMax", SelectString(simpleColumnPath),
      SummaryParameters[Long](partByCount = Some(3), ranges = Some(Seq(range))))
    v1TypicalRequest(message){ events =>
      events.length shouldBe 3
      events.head shouldBe Event("2013-01-19T22:13:40Z".toMillis, 32)
      events.last shouldBe Event("2013-01-19T22:14:10Z".toMillis, 20)
    }
  }

  test("summarizeMax on uneven data") {
    val range = RangeInterval(
               start = Some("2013-01-19T22:13:10Z".toMillis),
        until = Some("2013-01-19T22:13:41Z".toMillis))
 
    val message = summaryRequest[Long]("SummarizeMax", SelectString(unevenColumnPath),
      SummaryParameters[Long](partByCount = Some(2), ranges = Some(Seq(range))))
    v1TypicalRequest(message){ events =>
      events.length shouldBe 2
      events.head shouldBe Event("2013-01-19T22:13:12Z".toMillis, 31)
      events.last shouldBe Event("2013-01-19T22:13:40Z".toMillis, 32)
    }
  }

}
