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

package nest.sparkle.time.protocol
import org.scalacheck.Arbitrary.arbitrary
import nest.sparkle.store.Event
import org.scalacheck.Arbitrary
import nest.sparkle.store.Store
import nest.sparkle.store.ram.WriteableRamColumn
import nest.sparkle.store.WriteableStore
import scala.reflect.runtime.universe._
import nest.sparkle.store.ram.WriteableRamStore
import nest.sparkle.util.RandomUtil.randomAlphaNum
import spray.util._
import scala.concurrent.ExecutionContext
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Gen

/** Utility for using scalacheck properties to generate Events */
object ArbitraryColumn extends PropertyChecks {
  def eventGenerator[T: Arbitrary, U:Arbitrary]() = {
    for {
      key <- arbitrary[T]
      value <- arbitrary[U]
    } yield {
      Event(key, value)
    }
  }

  /** import this to allow generating Events in scalacheck generators */
  implicit def arbitraryEvent[T:Arbitrary, U:Arbitrary] = Arbitrary(eventGenerator[T,U]())

  /** (currently unused, causes compiler problems in 2.10.3.  retry in 2.10.4 or 2.11) */
  def arbitraryColumn[T: TypeTag: Ordering: Arbitrary, U: TypeTag: Arbitrary](prefix: String, storage: WriteableRamStore) // format: OFF
      (implicit execution: ExecutionContext): (String, Seq[Event[T, U]]) = { // format: ON
    val eventListGenerator = arbitrary[List[Event[T, U]]]
    val eventsGenerator = eventListGenerator.asInstanceOf[Gen[List[Event[T, U]]]]
    val storedEventsGenerator =
      eventsGenerator.map { events =>
        val columnName = prefix + randomAlphaNum(4)
        val column = storage.writeableColumn[T, U](columnName).await
        column.write(events)
        (columnName, events)
      }
    storedEventsGenerator.sample.get
  }
}

/** utility for testing the DomainRange transform */
object TestDomainRange {
  /** return the min max of domain and range of a list of events */
  def minMaxEvents(events: Seq[Event[Long, Double]]): (Long, Long, Double, Double) = {
    val domainMin = events.map { case Event(k, v) => k }.min
    val domainMax = events.map { case Event(k, v) => k }.max
    val rangeMin = events.map { case Event(k, v) => v }.min
    val rangeMax = events.map { case Event(k, v) => v }.max

    (domainMin, domainMax, rangeMin, rangeMax)
  }

}
