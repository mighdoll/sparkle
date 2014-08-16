package nest.sparkle.util

import spray.http.HttpResponse
import org.scalatest.Suite
import org.scalatest.FunSuite
import org.scalatest.Matchers

/** mix in to a Suite to get the expectHeader method, for string based header tests */
trait ExpectHeader {
  self: Suite with Matchers =>

  /** verify that an http response contains an expected http header */
  def expectHeader(headerName: String, value: String)(implicit response: HttpResponse) {
    val headerValue =
      response.headers.find(_.name.toLowerCase == headerName.toLowerCase) match {
        case None         => fail(s"header not found: $headerName")
        case Some(header) => header.value
      }
    headerValue shouldBe value
  }

}