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
import spray.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll
import java.nio.file.Files
import akka.actor.TypedActor
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.httpx.SprayJsonSupport._

import scala.concurrent.duration._
import nest.sparkle.util.Repeating.repeatWithDelay

trait DirectoryRegistryFixture extends FunSuite 
  with ScalatestRouteTest with DataService with BeforeAndAfterAll {
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  def executionContext = system.dispatcher
  override def testConfig = ConfigServer.loadConfig() 
  val dir = Files.createTempDirectory("testDirectoryRegistry")
  val registry = DirectoryDataRegistry(dir)
  val store = PreloadedStore(List(SampleData))

    /** utility routine to wait for files to be noticed (sometimes takes
   *  a while on mac os) */
  def awaitFileCount(targetCount: Int): Array[String] = {
    implicit val timeout = RouteTestTimeout(12.seconds)
    val filesOpt =
      repeatWithDelay(110) {
        Get("/info") ~> route ~> check {
          val files = responseAs[Array[String]]
          if (files.length == targetCount)
            Some(files)
          else
            None
        }
      }
    // return array or empty array if not found
    val result = filesOpt.toArray.flatten
    assert(result.length === targetCount)
    result
  }

  override def afterAll() {
    TypedActor(system).stop(registry)
    Files.deleteIfExists(dir)
  }

}
