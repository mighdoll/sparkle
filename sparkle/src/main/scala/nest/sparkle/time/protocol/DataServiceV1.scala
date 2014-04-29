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
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.routing.Directives
import nest.sparkle.store.Store
import nest.sparkle.time.protocol.ResponseJson.StreamsMessageFormat
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.server.RichComplete
import nest.sparkle.util.ObservableFuture._
import com.typesafe.config.Config

/**
 * Provides the v1 sparkle data api
 */
trait DataServiceV1 extends Directives with RichComplete with CorsDirective {
  implicit def executionContext: ExecutionContext
  def store: Store
  def rootConfig:Config

  val api = StreamRequestApi(store, rootConfig)

  lazy val v1protocol = {
    cors {
      pathPrefix("v1") {
        postDataRequest ~
        columnsRequest  ~
        dataSetsRequest
      }
    }
  }

  private lazy val postDataRequest = // format: OFF
    path("data") {
        post {
          entity(as[StreamRequestMessage]) {
            request =>
              val futureStreams = api.httpStreamRequest(request.message)
              val futureMessage = futureStreams.map {
                streams =>
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
    } // format: ON

  private lazy val columnsRequest =
    path("columns" / Rest) { dataSetName =>
        if (dataSetName.isEmpty) {
          // This will happen if the url has just a slash after 'columns'
          complete(StatusCodes.NotFound -> "DataSet not specified")
        } else {
          val futureColumnNames = store.dataSet(dataSetName).flatMap { dataSet =>
            dataSet.childColumns.map { columnPath =>
              val (_, columnName) = Store.setAndColumn(columnPath)
              columnName
            }.toFutureSeq
          }
          richComplete(futureColumnNames)
        }
    }

  private lazy val dataSetsRequest =
    path("datasets" / Rest) { dataSetName =>
        if (dataSetName.isEmpty) {
          // This will happen if the url has just a slash after 'columns'
          complete(StatusCodes.NotFound -> "DataSet not specified")
        } else {
          val futureNames = store.dataSet(dataSetName).flatMap { dataSet =>
            dataSet.childDataSets.map { dataSet =>
              val (_, childName) = Store.setAndColumn(dataSet.name)
              childName
            }.toFutureSeq
          }
          richComplete(futureNames)
        }
    }
}

