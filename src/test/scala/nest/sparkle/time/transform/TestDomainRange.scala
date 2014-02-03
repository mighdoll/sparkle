/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

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
import nest.sparkle.time.protocol.ArbitraryColumn
import nest.sparkle.time.protocol.TestDomainRange.minMaxEvents

class TestDomainRange extends FunSuite with Matchers with PropertyChecks {

  import ArbitraryColumn.arbitraryEvent
  import ExecutionContext.Implicits.global

  test("DomainRange for an arbitrary list of events") {
    forAll{ events: List[Event[Long, Double]] =>
      val ramColumn = WriteableRamColumn[Long, Double]("DomainTest")
      ramColumn.write(events).await
      val stream = DomainRange(ramColumn, JsObject())
      val dataSeq = stream.dataStream.toFutureSeq.await.flatten
      dataSeq.length shouldBe 1
      if (events.length > 0) {
        val (domainMin, domainMax, rangeMin, rangeMax) = minMaxEvents(events)

        // LATER consider using a json decoder to make this shorter (rather than decoding manually)
        // see testV1Api for an example

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
