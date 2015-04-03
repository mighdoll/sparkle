package nest.sparkle.loader

import scala.concurrent.duration._

import org.scalatest.{ FunSuite, Matchers }

import com.typesafe.config.Config

import akka.actor.ActorSystem
import akka.actor.ActorRefFactory
import akka.testkit._
import spray.http.MediaTypes.{ `text/tab-separated-values`, `text/csv` }
import spray.http.MediaRanges.`*/*`
import spray.http.{ HttpResponse, HttpRequest, HttpEntity, HttpHeader }
import spray.http.StatusCodes.{NotAcceptable, NotFound}
import spray.http.HttpHeaders.Accept
import spray.httpx.RequestBuilding
import spray.httpx.SprayJsonSupport._
import spray.routing.RoutingSettings
import spray.testkit.ScalatestRouteTest

import nest.sparkle.measure.MeasurementToTsvFile
import nest.sparkle.store.Store
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.time.server.{DataAdminService, DataAdminService$, ConcreteBaseDataAdminService}
import nest.sparkle.time.protocol.ExportData
import nest.sparkle.time.protocol.AdminProtocol.ExportDataFormat
import nest.sparkle.util.ExpectHeader

class TestAdminPageDownload extends FunSuite with Matchers with CassandraStoreTestConfig
    with ExpectHeader with RequestBuilding {

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
    withLoadedFile("epochs.csv") { (store, system) =>
      val admin = new DataAdminTestService(store, rootConfig)(system)
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

class DataAdminTestService
    ( override val store: Store, override val rootConfig: Config )
    ( implicit override val actorSystem: ActorSystem)
    extends FunSuite with DataAdminService with ScalatestRouteTest with Matchers {

  def actorRefFactory = actorSystem // connect the DSL to the test ActorSystem
  implicit def executionContext = actorSystem.dispatcher

  implicit val routeTimeout: RouteTestTimeout = RouteTestTimeout(7.seconds)

  implicit override val measurements =
    new MeasurementToTsvFile("/tmp/sparkle-admin-tests")(executionContext)

  def fetchRequest(request: HttpRequest)(fn: HttpResponse => Unit): Unit = {
    request ~> sealRoute(routes) ~> check {
      fn(response)
    }
  }

}