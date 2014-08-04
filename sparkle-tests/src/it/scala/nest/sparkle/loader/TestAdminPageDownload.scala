package nest.sparkle.loader

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.time.server.AdminService
import akka.actor.ActorSystem
import nest.sparkle.store.Store
import com.typesafe.config.Config
import spray.testkit.ScalatestRouteTest
import nest.sparkle.time.server.ConcreteAdminService
import nest.sparkle.time.protocol.ExportData
import nest.sparkle.time.protocol.AdminProtocol.ExportDataFormat
import spray.httpx.SprayJsonSupport._
import spray.routing.RoutingSettings
import scala.concurrent.duration._
import akka.actor.ActorRefFactory
import akka.testkit._

class TestAdminPageDownload extends FunSuite with Matchers with CassandraTestConfig {
  test("download a .tsv file via the web admin interface") {
    withLoadedPath("epochs.csv", "epochs.csv") { (store, system) =>
      val admin = new AdminTestService(store, rootConfig)(system)
      admin.fetchRequest()
    }
  }
}

class AdminTestService(override val store: Store, override val rootConfig: Config)(implicit override val system:ActorSystem)
    extends FunSuite with AdminService with ScalatestRouteTest {

  def actorRefFactory = system // connect the DSL to the test ActorSystem
  def executionContext = system.dispatcher
  
  def fetchRequest() {
    Post("/fetch", ExportData("epochs")) ~> route ~> check {
      println(s"foo $entity")
    }
  }
  

}