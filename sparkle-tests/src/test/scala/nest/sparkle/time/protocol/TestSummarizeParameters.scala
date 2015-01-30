package nest.sparkle.time.protocol
import spray.json.DefaultJsonProtocol._

class TestSummarizeParameters extends PreloadedRamService with StreamRequestor {

  test("no partSize and partCount summarizes into 1 partition") {
    val message = summaryRequest[Long]("SummarizeCount", params = SummaryParameters())
    v1TypicalRequest(message){ events =>
      events.length shouldBe 1
      events(0).value shouldBe 2
    }
  }

}