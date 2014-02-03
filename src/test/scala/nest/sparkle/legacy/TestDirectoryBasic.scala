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

package nest.sparkle.legacy

import org.scalatest.FunSuite
import org.scalatest.Matchers
import spray.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll
import java.nio.file.Paths
import akka.actor.TypedActor
import nest.sparkle.time.server.ConfigServer
import nest.sparkle.time.server.DataService
import nest.sparkle.time.protocol.TestDataService

class TestDirectoryBasic extends FunSuite with Matchers
  with ScalatestRouteTest with TestDataService with BeforeAndAfterAll {
  override def testConfig = ConfigServer.loadConfig() 
  val dir = Paths.get("src", "test", "resources")
  override val registry = DirectoryDataRegistry(dir)
  val store = PreloadedStore(List(SampleData))

  test("serve file info from directory") {
    Get("/info/epochs.csv") ~> route ~> check {
      assert(status.intValue === 200)
    }
  }
  
  test("serve nested file info from directory") {
    Get("/info/subdir/sub.csv") ~> route ~> check {
      assert(status.intValue === 200)
    }
  }
  
  test("serve data from directory") {
    Get("/data/money/subdir/sub.csv") ~> route ~> check {
      assert(status.intValue === 200)
    }
  }
  
  test("missing dataset serves 404") {
    Get("/data/foo/bar") ~> route ~> check {
      assert(status.intValue === 404)
    }
  }
  
  test("missing column serves 404") {
    Get("/data/a-missing-directory/epochs.csv") ~> route ~> check {
      assert(status.intValue === 404)
    }
  }
  
  override def afterAll() {
    TypedActor(system).stop(registry)
  }
  
}
