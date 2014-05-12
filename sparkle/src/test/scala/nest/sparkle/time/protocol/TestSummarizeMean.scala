package nest.sparkle.time.protocol

import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.Event
import nest.sparkle.time.transform.SummaryTransform.Events
import spire.math._
import spire.implicits._

class TestSummarizeMean extends TestStore with StreamRequestor with TestDataService {

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
    val message = streamRequest("SummarizeMean", selector = SelectString(simpleColumnPath),
      range = RangeParameters[Long](maxResults = 1))

    v1Request(message){ events =>
      events.length shouldBe 1
      events.head shouldBe mean[Long, Double](events)
    }
  }

  test("summarizeMean uneven set to one") {
    val message = streamRequest("SummarizeSum", selector = SelectString(unevenColumnPath),
      range = RangeParameters[Long](maxResults = 1))

    v1Request(message){ events =>
      events.length shouldBe 1
      events.head shouldBe mean(events)
    }
  }

}