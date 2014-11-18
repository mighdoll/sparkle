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

import scala.reflect.runtime.universe._
import scala.concurrent.duration._

import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._

class TestV1Api extends PreloadedRamStore with StreamRequestor with TestDataService {
//  implicit val routeTestTimeout = RouteTestTimeout(1.hour)
  nest.sparkle.util.InitializeReflection.init

  test("List columns for known dataset") {
    val path = s"/v1/columns/$testId"
    Get(path) ~> v1protocol ~> check {
      val columns = responseAs[Seq[String]]
      columns.length shouldBe 3
      columns should contain(testColumnName)
    }
  }

  test("Non existant dataset should get a 404") {
    val path = s"/v1/columns/noexist"
    Get(path) ~> v1protocol ~> check {
      response.status shouldBe StatusCodes.NotFound
    }
  }

  // cors is causing a 405 instead of a 404.
  test("Missing dataset should get a 404") {
    val path = s"/v1/columns"
    Get(path) ~> sealRoute(v1protocol) ~> check {
      response.status shouldBe StatusCodes.NotFound
    }
  }

}
