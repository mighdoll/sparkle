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

package nest.sparkle.util

import spray.http.HttpHeaders
import spray.testkit.ScalatestRouteTest
import org.scalatest.Matchers

/** Mix in to a test suite to get some utility methods for validating Header responses */
trait ExpectHeaders {
  self: ScalatestRouteTest with Matchers =>

  /** Fail a scalatest test if a specified header isn't present in a response,
    * or if the headers value doesn't match a provided function.
    */
  def expectHeader(expect: HttpHeaders.ModeledCompanion)(testFn: String => Boolean) {
    findHeader(expect) match {
      case Some(foundValue) => testFn(foundValue) shouldBe true
      case None             => fail
    }
  }

  /** Fail a scalatest test if a specified header is present in a response */
  def expectNoHeader(expect: HttpHeaders.ModeledCompanion) {
    findHeader(expect) match {
      case Some(_) => fail
      case None    =>
    }
  }

  /** return a header value if it's in the response's headers */
  private def findHeader(expect: HttpHeaders.ModeledCompanion): Option[String] = {
    headers.collectFirst {
      case header if header.lowercaseName == expect.lowercaseName => header.value
    }
  }

}
