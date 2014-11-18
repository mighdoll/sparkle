package nest.sparkle.time.protocol

import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.Event
import nest.sparkle.time.transform.SummaryTransform.Events
import spire.math._
import spire.implicits._

class TestSummarizeMean extends PreloadedRamStore with StreamRequestor with TestDataService {
  nest.sparkle.util.InitializeReflection.init

  def mean[T: Numeric, U: Numeric](events: Events[T, U]): Event[T, U] = {
    val keys = events.map { case Event(k, v) => k }
    val values = events.map{ case Event(k, v) => v }
    val sumKeys = keys.reduceLeft { _ + _ }
    val sumValues = values.reduceLeft { _ + _ }

    val meanKey = sumKeys / events.length
    val meanValue = sumValues / events.length
    Event(meanKey, meanValue)
  }

  test("summarizeMean simple set to one") {
    val message = summaryRequestOne[Long]("SummarizeMean", selector = SelectString(simpleColumnPath))

    v1TypicalRequest(message){ events =>
      events.length shouldBe 1
      events.head shouldBe mean[Long, Double](simpleEvents)
    }
  }

  test("summarizeMean uneven set to one") {
    val message = summaryRequestOne[Long]("SummarizeMean", selector = SelectString(unevenColumnPath))

    v1TypicalRequest(message){ events =>
      events.length shouldBe 1
      events.head shouldBe mean(unevenEvents)
    }
  }

}