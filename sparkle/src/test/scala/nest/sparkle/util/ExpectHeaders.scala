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