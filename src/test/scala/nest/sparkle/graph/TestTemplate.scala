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

import org.scalatest.FunSuite
import org.scalatest.Matchers
import spray.testkit.ScalatestRouteTest
import spray.http.StatusCodes

class TestTemplate extends FunSuite with Matchers with ScalatestRouteTest
    with DataService {
  override def testConfig = ConfigServer.loadConfig()   
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  def executionContext = system.dispatcher

  val registry = PreloadedRegistry(Nil)
  val store = PreloadedStore(List(SampleData))
  override def webRoot = Some("src/test/resources/subdir")
  
  test("serve index from custom webRoot") {
    Get("/") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "template boo\n"
    }
  }
}
