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

package nest.sparkle.time.protocol

import scala.concurrent.ExecutionContext
import spray.httpx.SprayJsonSupport._
import spray.routing.Directives
import nest.sparkle.store.Storage
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.StreamsMessageFormat
import spray.routing.Directives

/** Provides the v1/data sparkle REST api */
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
        entity(as[StreamRequestMessage]) { request =>
          val futureStreams = api.handleStreamRequest(request.message)
          val futureMessage = futureStreams.map { streams =>
            StreamsMessage(
              requestId = request.requestId,
              realm = request.realm,
              traceId = request.traceId,
              messageType = MessageType.Streams,
              message = streams
            )
          }
          complete(futureMessage)
        }
      }
    }

}
