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
import com.typesafe.config.ConfigFactory
import akka.actor.ActorRefFactory
import spray.routing.HttpService

class TestCustomApi extends FunSuite with Matchers with ScalatestRouteTest with ConfiguredDataService {
  override def testConfig = ConfigServer.loadConfig(configFile = "testCustomApi.conf")
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  val executionContext = system.dispatcher
  val registry = new PreloadedRegistry(List(SampleData))(system.dispatcher)
  val store = PreloadedStore(List(SampleData))
  def config = testConfig
  
  test("custom api") {
    Get("/foo") ~> route ~> check {
      assert(status.intValue === 200)
      assert(responseAs[String] === "bah")
    }
  }
}

class SampleApi(actorRefFactory: ActorRefFactory, dataRegistry:DataRegistry)
    extends ApiExtension(actorRefFactory, dataRegistry) with HttpService {

  def route =
    get {
      path("foo") {
        complete("bah")
      }
    }
}
