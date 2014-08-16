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
import spray.http.MediaTypes.{ `text/tab-separated-values`, `text/csv` }
import spray.http.MediaRanges.`*/*`
import scala.concurrent.duration._
import akka.actor.ActorRefFactory
import akka.testkit._
import spray.http.{ HttpResponse, HttpRequest, HttpEntity, HttpHeader }
import spray.http.StatusCodes.{NotAcceptable, NotFound}
import nest.sparkle.util.ExpectHeader
import spray.http.HttpHeaders.Accept
import spray.httpx.RequestBuilding

class TestAdminPageDownload extends FunSuite with Matchers with CassandraTestConfig with ExpectHeader with RequestBuilding {

  def epochTest(headers: HttpHeader*) {
    epochRequestWithHeaders(headers: _*) { implicit response =>
      val entity = response.entity.asInstanceOf[HttpEntity.NonEmpty]
      entity.contentType.toString shouldBe "text/tab-separated-values"
      expectHeader("Content-Disposition", "attachment; filename=epochs.tsv")
      response.entity.asString.lines.toList.length shouldBe 2752
    }
  }

  def epochRequestWithHeaders(headers: HttpHeader*)(fn: HttpResponse => Unit) {
    requestWithLoadedEpochs(Get("/fetch/epochs").withHeaders(headers: _*))(fn)
  }
  
  def requestWithLoadedEpochs(request:HttpRequest)(fn: HttpResponse => Unit) {
    withLoadedPath("epochs.csv", "epochs.csv") { (store, system) =>
      val admin = new AdminTestService(store, rootConfig)(system)
      admin.fetchRequest(request) {
        response =>
          fn(response)
      }
    }
  }

  test("download a .tsv file via the web admin interface, no Accept headers") {
    epochTest()
  }

  test("download a .tsv file via the web admin interface, Accept */*") {
    epochTest(Accept(`*/*`))
  }

  test("download a .tsv file via the web admin interface, Accept text/tab-separated-values") {
    epochTest(Accept(`text/tab-separated-values`))
  }
  
  test("download a .tsv file via the web admin interface, Accept text/csv should fail") {
    epochRequestWithHeaders(Accept(`text/csv`)) { response =>
      response.status shouldBe NotAcceptable
    }
  }
  
  test("download a missing .tsv file via the web admin interface gives NotFound") {
    requestWithLoadedEpochs(Get("fetch/_missing")) { response =>
      response.status shouldBe NotFound
    }
  }

}

class AdminTestService(override val store: Store, override val rootConfig: Config)(implicit override val system: ActorSystem)
    extends FunSuite with AdminService with ScalatestRouteTest with Matchers {

  def actorRefFactory = system // connect the DSL to the test ActorSystem
  def executionContext = system.dispatcher

  def fetchRequest(request: HttpRequest)(fn: HttpResponse => Unit): Unit = {
    request ~> sealRoute(route) ~> check {
      fn(response)
    }
  }

}