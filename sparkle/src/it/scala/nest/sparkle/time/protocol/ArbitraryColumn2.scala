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

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.prop.PropertyChecks

import nest.sparkle.store.Event

// TODO DRY with ArbitraryColumn

/** Utility for using scalacheck properties to generate Events */
object ArbitraryColumn2 extends PropertyChecks {
  def eventGenerator[T: Arbitrary, U: Arbitrary]() = {
    for {
      key <- arbitrary[T]
      value <- arbitrary[U]
    } yield {
      Event(key, value)
    }
  }

  /** import this to allow generating Events in scalacheck generators */
  implicit def arbitraryEvent[T: Arbitrary, U: Arbitrary] = Arbitrary(eventGenerator[T, U]())
}
