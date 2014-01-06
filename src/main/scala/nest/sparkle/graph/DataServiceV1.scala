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

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.{ postfixOps, reflectiveCalls }
import scala.concurrent.ExecutionContext
import nest.sparkle.graph.DataProtocolJson._
import spray.httpx.SprayJsonSupport._
import spray.routing.Directives

trait DataServiceV1 extends Directives {
  implicit def executionContext: ExecutionContext
  def store: Storage
  val api = DataRequestApi(store)

  lazy val v1protocol = {
    pathPrefix("v1") {
      dataRequest
    }
  }

  private lazy val dataRequest =
    path("data") {
      post {
        entity(as[DataRequest]) { request =>
          complete(api.request(request))
        }
      }
    }

}
