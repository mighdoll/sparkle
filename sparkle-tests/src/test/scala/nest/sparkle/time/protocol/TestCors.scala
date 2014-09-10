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

import spray.httpx.SprayJsonSupport._
import spray.http.HttpHeaders._
import spray.http.HttpHeaders
import spray.http.HttpHeader
import nest.sparkle.util.ExpectHeaders
import spray.testkit.ScalatestRouteTest
import spray.json.DefaultJsonProtocol._
import spray.json._
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat

/** handy for tests to use localhost as an origin */
trait LocalHostOrigin {
  self: ScalatestRouteTest =>

  lazy val localhost = "http://localhost"
  lazy val addOriginLocalHost = addHeader(Origin(List(localhost)))
}

/* corsHosts is static per route, so we create separate test suites for various settings
 * of corsHosts. */

class TestCors extends TestStore with StreamRequestor with TestDataService with ExpectHeaders
    with LocalHostOrigin {
  lazy val anyHost = "*"
  override lazy val corsHosts = List(anyHost)
  nest.sparkle.util.InitializeReflection.init

  def expectCorsHeaders() {
    expectHeader(`Access-Control-Allow-Origin`) { _ == localhost } // or should we return *
    expectHeader(`Access-Control-Allow-Methods`) { _.contains("POST") }
    expectHeader(`Access-Control-Allow-Headers`) { allowedString =>
      val allowed = allowedString.split(",").map(_.trim).toSet
      val expected = Set(
        "Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language",
        "Host", "Referer", "User-Agent", "Authorization", "DNT", "Cache-Control", "Keep-Alive", "X-Requested-With"
      )
      if (allowed != expected) {
        println(s"""failing TestCors.Access-Control-Allow-Headers:
            allowed: $allowed
            expected: $expected""")
      }
      allowed == expected
    }
  }

  test("cors headers visible when cors is enabled") {
    Options("/v1/data") ~> addOriginLocalHost ~> v1protocol ~> check {
      expectCorsHeaders()
    }

    val requestMessage = streamRequest("Raw", JsObject())
    Post("/v1/data", requestMessage) ~> addOriginLocalHost ~> v1protocol ~> check {
      expectCorsHeaders()
    }

  }
}

class TestCorsOff extends TestStore with StreamRequestor with TestDataService
    with ExpectHeaders with LocalHostOrigin {

  def expectNoCorsHeaders() {
    expectNoHeader(`Access-Control-Allow-Origin`)
    expectNoHeader(`Access-Control-Allow-Methods`)
  }

  test("cors headers not visible when cors is disabled") {
    Options("/v1/data") ~> addOriginLocalHost ~> v1protocol ~> check {
      expectNoCorsHeaders()
    }

    val requestMessage = streamRequest("Raw", JsObject())
    Post("/v1/data", requestMessage) ~> addOriginLocalHost ~> v1protocol ~> check {
      expectNoCorsHeaders()
    }
  }
}

class TestCorsHosts extends TestStore with StreamRequestor with TestDataService
    with ExpectHeaders with LocalHostOrigin {
  lazy val hosts = s"$localhost,http://otherhost.com"
  override lazy val corsHosts = hosts.split(",").map(_.trim).toList

  def expectCorsHeaders() {
    expectHeader(`Access-Control-Allow-Origin`) { _ == localhost }
  }

  test("cors list of hosts matches") {
    Options("/v1/data") ~> addOriginLocalHost ~> v1protocol ~> check {
      expectCorsHeaders()
    }

    val requestMessage = streamRequest("Raw", JsObject())
    Post("/v1/data", requestMessage) ~> addOriginLocalHost ~> v1protocol ~> check {
      expectCorsHeaders()
    }
  }
}
