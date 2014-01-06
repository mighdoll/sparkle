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
import spray.httpx.SprayJsonSupport._
import nest.sparkle.graph.DataServiceJson._
import spray.http.DateTime
import com.typesafe.config.ConfigFactory

class TestRestApi extends FunSuite with Matchers with ScalatestRouteTest with DataService {
  override def testConfig = ConfigServer.loadConfig() 
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  val executionContext = system.dispatcher
  
  val registry = new PreloadedRegistry(List(SampleData))(system.dispatcher)
  val store = PreloadedStore(List(SampleData))

  test("data request - simple request ") {
    Get("/data/p90/sample") ~> route ~> check {
      val samples = responseAs[Array[(Long, Double)]]
      samples.length should be (7)
    }
  }
  
  test("data request - max values") {
    Get("/data/p90/sample?max=5") ~> route ~> check {
      val samples = responseAs[Array[(Long, Double)]]
      samples.length should be (5)
    }
  }

  test("metric info request ") {
    Get("/column/p90/sample") ~> route ~> check {
      val info = responseAs[MetricInfo]
      info.range should be(Some(20, 32))
      info.domain.get._1 should be(DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get.clicks)
    }
  }
  
  test("dataset info request ") {
    Get("/info/sample") ~> route ~> check {
      val info = responseAs[DataSetInfo]
      info.domain.get._1 should be(DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get.clicks)
    }
  }
  
  test("nonexistent api") {
    Get("/bizdoodle") ~> route ~> check {
      assert(status.intValue === 404)
      val info = responseAs[String]
    }
  }

  test("nonexistent data") {
    Get("/data/arrgle/oog") ~> route ~> check {
      val info = responseAs[String]
      assert(status.intValue === 404)
    }
  }

  test("nonexistent set") {
    Get("/info/arrgle") ~> route ~> check {
      val info = responseAs[String]
      assert(status.intValue === 404)
    }
  }
  
  test("nonexistent metric") {
    Get("/column/boobah/sample") ~> route ~> check {
      assert(status.intValue === 404)
      val info = responseAs[String]
    }
  }
  
  test("unique values from column") {
    Get("/column/p90/sample?uniques=true") ~> route ~> check {      
      assert(status.intValue === 200)
      val info = responseAs[MetricInfoWithUniques]
      info.uniqueValues.length should be (6)
      info.range should be(Some(20, 32))
      info.domain.get._1 should be(DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get.clicks)
    }
  }

}
