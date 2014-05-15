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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.util.control.Exception.catching

import com.typesafe.config.Config

import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.routing.{Directives, ExceptionHandler, StandardRoute}
import spray.json._

import nest.sparkle.store.Store
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.{ StreamsMessageFormat, StatusMessageFormat }
import nest.sparkle.time.server.RichComplete
import nest.sparkle.util.Log
import nest.sparkle.util.ObservableFuture.WrappedObservable

/** Provides the v1 sparkle data api
  */
trait DataServiceV1 extends Directives with RichComplete with CorsDirective with Log {
  implicit def executionContext: ExecutionContext
  def store: Store
  def rootConfig: Config

  val api = StreamRequestApi(store, rootConfig)

  lazy val v1protocol = {
    cors {
      pathPrefix("v1") {
        postDataRequest ~
          columnsRequest ~
          dataSetsRequest
      }
    }
  }

  val exceptionHandler = ExceptionHandler {
    case err => // TODO this should report the request if it can.  Alsodoesn't 
      unconnectedError(Status(999, s"unexpected error. $err"))
  }

  private lazy val postDataRequest =
    path("data") {
      handleExceptions(exceptionHandler) {
        post {
          entity(as[String]) { entityString =>
            ctx =>
            for {
              postedJson <- catchingFormatError(entityString){ entityString.asJson }
              streamRequest <- catchingFormatError(entityString) {
                postedJson.convertTo[StreamRequestMessage](StreamRequestMessageFormat)
              }
            } {
              log.info(s"DataServiceV1.request: ${postedJson.compactPrint}")
              val futureResponse = httpDataRequest(streamRequest)
              futureResponse.onComplete {
                case Success(response: StreamsMessage) => ctx.complete(response)
                case Failure(err)                      => ctx.complete(streamError(streamRequest, err))
              }
            }
          }
        }
      }
    }

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

  private def httpDataRequest(request: StreamRequestMessage): Future[StreamsMessage] = {
    val futureResponse = {
      for {
        streams <- api.httpStreamRequest(request.message)
      } yield {
        val streamsResponse = StreamsMessage(
          requestId = request.requestId,
          realm = request.realm,
          traceId = request.traceId,
          messageType = MessageType.Streams,
          message = streams
        )
        log.info(s"StreamsMessage ${streamsResponse.takeData(3).toJson.compactPrint}")
        streamsResponse
      }
    }
    futureResponse
  }

  /** translate errors in processing to appropriate Status messages to the client */
  private def streamError(request: StreamRequestMessage, error: Throwable): StatusMessage = {
    val status =
      error match {
        case err: DeserializationException =>
          Status(603, s"parameter error in transform request: ${request.toJson.compactPrint}")
        case err: MalformedSourceSelector =>
          Status(604, s"parameter error in source selector: ${request.toJson.compactPrint}")
        case err => 
          Status(999, s"unknown error $err in ${request.toJson.compactPrint}")
      }
    val statusMessage = StatusMessage(requestId = request.requestId, realm = request.realm,
      traceId = request.traceId, messageType = MessageType.Status, message = status)
    log.warn(s"streamError ${status.code} ${status.description}: request: ${request.toJson.prettyPrint} ")
    statusMessage
  }

  private def unconnectedError(status: Status): StandardRoute = {
    log.warn(s"SStreamError $status")
    val statusMessage = StatusMessage(requestId = None, realm = None, traceId = None,
      messageType = MessageType.Status, message = status)
    complete(statusMessage)
  }

  /** catch errors while decoding the StreamsRequestMessage */
  private def catchingFormatError[T](entityString: String)(fn: => T): Try[T] = {
    def parseError(err: Throwable) {
      val status = Status(606, s"request format error. $err request: $entityString")
      unconnectedError(status)
    }

    val triedFn = catching(classOf[RuntimeException]).withTry { fn }
    triedFn.failed.foreach { err => parseError(err) }
    triedFn
  }

}

