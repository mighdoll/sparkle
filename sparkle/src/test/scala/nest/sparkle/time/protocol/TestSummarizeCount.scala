package nest.sparkle.time.protocol

import spray.json.DefaultJsonProtocol._

import nest.sparkle.store.Event
import nest.sparkle.time.transform.SummaryTransform.Events
import spire.math._
import spire.implicits._

class TestSummarizeCount extends TestStore with StreamRequestor with TestDataService {

  def count[T: Numeric, U: Numeric](events: Events[T, U]): Event[T, U] = {
    val keys = events.map { case Event(k, v) => k }
    val sumKeys = keys.reduceLeft { _ + _ }

    val meanKey = sumKeys / events.length
    val numericValue = implicitly[Numeric[U]]

    Event(meanKey, numericValue.fromInt(events.length))
  }

  def expectOnePerSimpleEvent[T, U](events: Events[T, U]) {
    events.length shouldBe 7
    val countOnePerPartition = events.map { case Event(k, v) => Event(k, 1) }
    events shouldBe countOnePerPartition
  }

  test("summarizeCount simple set to one") {
    val message = summaryRequestOne[Long]("SummarizeCount", selector = SelectString(simpleColumnPath))

    v1Request(message){ events =>
      events.length shouldBe 1
      events.head shouldBe count[Long, Double](simpleEvents)
    }
  }

  test("summarizeCount simple set to 7") {
    val message = summaryRequest[Long]("SummarizeCount", selector = SelectString(simpleColumnPath),
      params = SummaryParameters(maxPartitions = Some(7)))

    v1Request(message){ events =>
      expectOnePerSimpleEvent(events)
    }
  }

  test("summarizeCount simple set to 10") {
    val message = summaryRequest("SummarizeCount", selector = SelectString(simpleColumnPath),
      params = SummaryParameters[Long](maxPartitions = Some(10)))

    v1Request(message){ events =>
      expectOnePerSimpleEvent(events)
    }
  }

}