package nest.sparkle.time.protocol

// TODO revise customApi test with current api

//import org.scalatest.FunSuite
//import org.scalatest.Matchers
//import spray.testkit.ScalatestRouteTest
//import akka.actor.ActorRefFactory
//import spray.routing.HttpService
//import nest.sparkle.time.server.ApiExtension
//import nest.sparkle.time.server.ConfigureSparkle
//import nest.sparkle.time.server.ConfiguredDataService
//import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable
//import spray.routing.Directive.pimpApply
//
//class TestCustomApi extends FunSuite with Matchers with ScalatestRouteTest with ConfiguredDataService {
//  override def testConfig = ConfigureSparkle.loadConfig(configResource = "testCustomApi.conf")
//  def actorRefFactory = system // connect the DSL to the test ActorSystem
//  val executionContext = system.dispatcher
//  val registry = new PreloadedRegistry(List(SampleData))(system.dispatcher)
//  val store = PreloadedStore(List(SampleData))
//  def config = testConfig
//
//  test("custom api") {
//    Get("/foo") ~> route ~> check {
//      assert(status.intValue === 200)
//      assert(responseAs[String] === "bah")
//    }
//  }
//}
//
//class SampleApi(actorRefFactory: ActorRefFactory, dataRegistry:DataRegistry)
//    extends ApiExtension(actorRefFactory, dataRegistry) with HttpService {
//
//  def route =
//    get {
//      path("foo") {
//        complete("bah")
//      }
//    }
//}
