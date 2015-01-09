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

package nest.sparkle.graph

import org.scalatest.{FunSuite, Matchers}

import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import nest.sparkle.http.FileLocation
import nest.sparkle.time.protocol.TestDataService
import nest.sparkle.util.Resources

/** test serving a custom webroot */
//class TestTemplate extends FunSuite with Matchers with ScalatestRouteTest with TestDataService {
////  val store = PreloadedStore(List(SampleData))
//  lazy val subdirPath = Resources.filePathString("subdir")
//  override def webRoot = Some(FileLocation(subdirPath))
//
//  test("serve index from custom web-root directory") {
//    Get("/") ~> route ~> check {
//      status shouldEqual StatusCodes.OK
//      responseAs[String] shouldEqual "template boo\n"
//    }
//  }
//}

// TODO Fixme
