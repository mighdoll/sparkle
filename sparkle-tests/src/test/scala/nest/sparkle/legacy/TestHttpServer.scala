///* Copyright 2013  Nest Labs
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.  */
//
//package nest.sparkle.legacy
//
//import org.scalatest.FunSuite
//import org.scalatest.Matchers
//import scala.concurrent.duration._
//import akka.actor.ActorSystem
//import akka.util.Timeout
//import spray.http.HttpMethods._
//import spray.httpx.SprayJsonSupport._
//import nest.sparkle.legacy.DataServiceJson._
//import spray.util._
//import akka.io.IO
//import spray.can.Http
//import spray.http._
//import akka.pattern.ask
//import akka.actor.Props
//import spray.client.pipelining._
//import akka.util.Timeout.durationToTimeout
//import nest.sparkle.time.server.ConfigServer
//import nest.sparkle.time.server.DataServer
//
//
//class TestHttpServer extends FunSuite with Matchers {
//
//  /** setup a test server  */
//  // we setup the server 'manually' rather than relying on e.g. SprayCanHttpServerApp for now,
//  // (LATER consider whether it's worth the time/trouble of setting up a new server for every test)
//  def withServer[T](fn: => T): T = {
//    val testConfig = ConfigServer.loadConfig()
//    implicit val serverSystem = ActorSystem("server", testConfig)
//    import serverSystem.dispatcher
//    val dataApi = new PreloadedRegistry(List(SampleData))
//    val preloadedStore = PreloadedStore(List(SampleData))
//
//    val dataService = serverSystem.actorOf(
//        Props{new DataServer(dataApi, preloadedStore)}, "sparkle-data-server")
//    implicit val timeout = Timeout(10.seconds)
//    val started = IO(Http) ? Http.Bind(dataService, "localhost", port = 17997)
//    started.await()
//
//    try {
//      fn
//    } finally {
//      serverSystem.shutdown()
//    }
//  }
//
//  /** setup a client pipeline to the test server */
//  def withClient[T](fn: (ActorSystem) => T): T = {
//    val testConfig = ConfigServer.loadConfig()
//    val clientSystem = ActorSystem("client", testConfig)
//
//    try {
//      fn(clientSystem)
//    } finally {
//      clientSystem.shutdown()
//    }
//
//  }
//
//  /** setup a unique server and client, and then run a test */
//  def withDataRequest[T](fn: ActorSystem => T) {
//    try {
//      withServer {
//        withClient { clientSystem =>
//          fn(clientSystem )
//        }
//      }
//    }
//  }
//
//  test("loopback server - basic data request") {
//    withDataRequest { system =>
//      implicit val _system = system
//      import system.dispatcher
//      val pipeline = addHeaders(HttpHeaders.Host("localhost", 17997)) ~> sendReceive ~> unmarshal[Array[(Long, Double)]]
//      val responseFuture = pipeline(Get("/data/p90/sample"))
//      val response = responseFuture.await(60.seconds)
//      response.length should be(7)
//    }
//  }
//
//}
