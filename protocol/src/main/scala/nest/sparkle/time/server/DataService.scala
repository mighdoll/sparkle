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

package nest.sparkle.time.server

import scala.concurrent.ExecutionContext

import spray.http.StatusCodes.NotFound
import spray.routing.Route
import nest.sparkle.time.server.JsServiceConfigJson.JsServiceConfigFormat
import spray.httpx.SprayJsonSupport._

import nest.sparkle.http.{HttpLogging, StaticContent}
import nest.sparkle.time.protocol.DataServiceV1
import nest.sparkle.util.Log

/** http API for serving data and static web content */
trait DataService extends StaticContent with DataServiceV1 with HttpLogging with Log {
  implicit def executionContext: ExecutionContext

  /** Subclasses can override customRoutes to provide additional routes.  The resulting
    * routes have priority and can replace any built in routes.
    */
  def customRoutes: Iterable[Route] = List()

  private def externalRoutes: Route =
    customRoutes.reduceLeftOption{ (a, b) => a ~ b } getOrElse reject()
  
  private val health: Route = {
    path("health") {
      dynamic {
        complete("ok")
      }
    }
  }

  lazy val jsConfig = JsServiceConfig(rootConfig.getInt("sparkle.web-socket.port"))

  private lazy val jsServiceConfig: Route = {
    get {
      path("serverConfig") {
        complete(jsConfig)
      }
    }
  }

  lazy val notFound = {
    unmatchedPath { path => complete((NotFound, "not found")) }
  }

  /** all api endpoints */
  val route = {  // format: OFF
    withRequestResponseLog {
      externalRoutes ~
      v1protocol ~
      get {
        staticContent ~
        jsServiceConfig ~
        health ~
        notFound
      }
    }
  } // format: ON
}

