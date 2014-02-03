package nest.sparkle.time.protocol

import nest.sparkle.time.server.DataService
import nest.sparkle.legacy.DataRegistry
import spray.testkit.ScalatestRouteTest
import org.scalatest.Suite

trait TestDataService extends DataService with ScalatestRouteTest {
  self: Suite =>
  override lazy val corsHosts = List("*")
  def actorRefFactory = system // connect the DSL to the test ActorSystem
  def executionContext = system.dispatcher

// Members declared in nest.sparkle.time.server.DataService   
  def registry: DataRegistry = ???      

}