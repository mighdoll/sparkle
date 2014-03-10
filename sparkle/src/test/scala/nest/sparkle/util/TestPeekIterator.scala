/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.util

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

class TestPeekIterator extends FunSuite with Matchers with PropertyChecks {
  implicit override val generatorDrivenConfig = PropertyCheckConfig(minSuccessful = 10)

  def peeked[T](list:List[T]) = PeekableIterator(list.toIterator)
  
  def peekedIteratorMatch(list: List[Int]) {
    peeked(list).toList shouldEqual list
  }
  
  def peekedTailMatch(list: List[Int]) {
    peeked(list).tail.toList shouldEqual list.tail
  }
  
  def peekedHeadOptionMatch(list:List[Int]) {
    peeked(list).headOption shouldEqual list.headOption    
  }

  test("peeked.headOption should match original") {
    forAll { peekedHeadOptionMatch(_) }
  }

  test("peeked iterator should match orignal iterator") {
    forAll { peekedIteratorMatch(_) }
  }

  test("peeked tail should match orignal iterator") {
    forAll { list: List[Int] =>
      whenever (list.length > 0) {
        peekedTailMatch(list)
      }
    }
  }

}
