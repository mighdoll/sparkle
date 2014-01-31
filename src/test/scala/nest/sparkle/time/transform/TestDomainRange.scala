package nest.sparkle.time.transform

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Gen
import org.scalacheck.Arbitrary.arbitrary
import nest.sparkle.store.Event
import org.scalacheck.Arbitrary
import nest.sparkle.store.Column
import spray.json.JsObject
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.concurrent.ExecutionContext
import nest.sparkle.util.ObservableFuture._
import spray.util._
import nest.sparkle.store.ram.WriteableRamColumn

class TestDomainRange extends FunSuite with Matchers with PropertyChecks {
  val eventGen = for {
    key <- arbitrary[Long]
    value <- arbitrary[Double]
  } yield {
    Event(key, value)
  }

  implicit val arbitraryEvent = Arbitrary(eventGen)
  import ExecutionContext.Implicits.global

  /** return the min max of domain and range of a list of events */
  def minMaxEvents(events: Seq[Event[Long, Double]]): (Long, Long, Double, Double) = {
    val domainMin = events.map { case Event(k, v) => k }.min
    val domainMax = events.map { case Event(k, v) => k }.max
    val rangeMin = events.map { case Event(k, v) => v }.min
    val rangeMax = events.map { case Event(k, v) => v }.max

    (domainMin, domainMax, rangeMin, rangeMax)
  }

  test("DomainRange for an arbitrary list of events") {
    forAll{ events: List[Event[Long, Double]] =>
      val ramColumn = WriteableRamColumn[Long, Double]("DomainTest")
      ramColumn.write(events).await
      val stream = DomainRange(ramColumn, JsObject())
      val dataSeq = stream.dataStream.toFutureSeq.await.flatten
      dataSeq.length shouldBe 1
      if (events.length > 0) {
        val (domainMin, domainMax, rangeMin, rangeMax) = minMaxEvents(events)

        // LATER consider using a json decoder rather than decoding manually

        val brackets = dataSeq(0).prettyPrint.collect { case '[' => 1 }.sum
        brackets shouldBe 5

        val domainJs = dataSeq(0).elements(0).asInstanceOf[JsArray]
        domainJs.elements(0).toString shouldBe "\"domain\""

        val domainJsMinMax = domainJs.elements(1).asInstanceOf[JsArray]
        domainJsMinMax.elements(0).convertTo[Long] shouldBe domainMin
        domainJsMinMax.elements(1).convertTo[Long] shouldBe domainMax

        val rangeJs = dataSeq(0).elements(1).asInstanceOf[JsArray]
        rangeJs.elements(0).toString shouldBe "\"range\""

        val rangeJsMinMax = rangeJs.elements(1).asInstanceOf[JsArray]
        rangeJsMinMax.elements(0).convertTo[Double] shouldBe rangeMin
        rangeJsMinMax.elements(1).convertTo[Double] shouldBe rangeMax

      } else {
        dataSeq(0).prettyPrint shouldBe """[["domain", []], ["range", []]]"""
      }
    }
  }
}