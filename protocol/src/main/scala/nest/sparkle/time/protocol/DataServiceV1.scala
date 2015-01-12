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

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scala.util.control.Exception.nonFatalCatch
import com.typesafe.config.Config
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.routing.{ Directives, ExceptionHandler, StandardRoute }
import spray.json._
import nest.sparkle.store.Store
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.{ StreamsMessageFormat, StatusMessageFormat }
import nest.sparkle.time.server.RichComplete
import nest.sparkle.util.Log
import nest.sparkle.util.ObservableFuture.WrappedObservable
import spray.routing.RequestContext
import akka.actor.ActorSystem
import nest.sparkle.time.transform.InvalidPeriod
import nest.sparkle.store.ColumnNotFound
import nest.sparkle.util.TryToFuture.FutureTry
import spray.routing.Route
import nest.sparkle.measure.Measurements

/** Provides the v1 sparkle data api
  */
trait DataServiceV1 extends Directives with RichComplete with CorsDirective with Log {
  def actorSystem: ActorSystem
  implicit def executionContext: ExecutionContext // TODO can we just use actorSystem.dispatcher here?
  def store: Store
  def rootConfig: Config
  def measurements:Measurements

  // (lazy to help test logging initialization order)
  lazy val api = StreamRequestApi(store, rootConfig)(actorSystem, measurements)

  lazy val v1protocol = {
    cors {
      pathPrefix("v1") { // format: OFF
        postDataRequest ~
        columnsRequest ~
        dataSetsRequest
      } // format: ON
    }
  }

  private lazy val postDataRequest =
    path("data") {
      handleExceptions(exceptionHandler) {
        post {
          entity(as[String]) { entityString =>
            parseStreamRequest(entityString) match {
              case Success(streamRequest) => completeDataRequest(streamRequest)
              case Failure(err)           => complete(err)
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

  /** catch-all in case we don't catch the error elsewhere */
  private val exceptionHandler = ExceptionHandler {
    case RequestParsingException(origMessage) => { ctx =>
      completeWithError(ctx, Status(606, s"Request could not be parsed.  $origMessage"))
    }

    case err => { ctx =>
      log.error("shouldn't we catch this error elsewhere?", err)
      completeWithError(ctx, Status(999, s"unexpected error. $err"))
    }
  }
  
  /** respond to the caller with the results of a processing their StreamRequest */
  private def completeDataRequest(request: StreamRequestMessage): Route = { ctx =>
    log.info(s"DataServiceV1.request: ${request.toLogging}")
    val futureResponse = httpDataRequest(request)
    futureResponse.onComplete {
      case Success(response: StreamsMessage) => ctx.complete(response)
      case Failure(err)                      => ctx.complete(streamError(request, err))
    }
  }

  /** return a StreamRequestMessage if it can be succesfully parsed from a string */
  private def parseStreamRequest(request: String): Try[StreamRequestMessage] = {
    val result =
      for {
        json <- nonFatalCatch.withTry { request.asJson }
        streamRequest <- nonFatalCatch.withTry { json.convertTo[StreamRequestMessage] }
      } yield {
        streamRequest
      }

    result match {
      case Failure(err) =>
        log.warn(s"parseStreamRequest failed to parse: $request", err)  
        Failure(RequestParsingException(request))
      case success => success
    }

  }

  /** process a streamRequest through the api engine, return the results mapped into
   *  a StreamsMessage.
   */
  private def httpDataRequest(request: StreamRequestMessage): Future[StreamsMessage] = {
    val futureResponse = {
      for {
        streams <- api.httpStreamRequest(request)
      } yield {
        val streamsResponse = StreamsMessage(
          requestId = request.requestId,
          realm = request.realm.map { orig => RealmToClient(orig.name) },
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
    val requestAsString = request.toJson.compactPrint
    val status =
      error match {
        case ColumnNotFound(msg) =>
          Status(601, s"Column not found.  $msg request: $requestAsString")
        case InvalidPeriod(msg) =>
          Status(603, s"Invalid period in Transform parameter.  $msg request: $requestAsString")
        case err: MalformedSourceSelector =>
          Status(603, s"parameter error in transform.  request: $requestAsString")
        case err: DeserializationException =>
          Status(604, s"parameter error in source selector.  request: $requestAsString")
        case CustomSourceNotFound(msg) =>
          Status(605, s"custom source selector not found: $msg.  request: $requestAsString")
        case AuthenticationFailed =>
          Status(611, s"Authentication failed.  request: $requestAsString")
        case AuthenticationMissing =>
          Status(612, s"Authentication missing.  request: $requestAsString")
        case ColumnForbidden(msg) =>
          Status(613, s"Access to column forbidden.  $msg request: $requestAsString")
        case err =>
          log.error("unexpected error processing request", err)
          Status(999, s"unknown error $err in $requestAsString")
      }
    val realmToClient = request.realm.map { orig => RealmToClient(orig.name) }
    val statusMessage = StatusMessage(requestId = request.requestId, realm = realmToClient,
      traceId = request.traceId, messageType = MessageType.Status, message = status)
    log.warn(s"streamError ${status.code} ${status.description}: request: $requestAsString ")
    statusMessage
  }

  /** error with no parsed request available */
  private def completeWithError(context: RequestContext, status: Status): Unit = {
    log.warn(s"StreamError $status")
    val statusMessage = StatusMessage(requestId = None, realm = None, traceId = None,
      messageType = MessageType.Status, message = status)
    context.complete(statusMessage)
  }
  
  case class RequestParsingException(origRequest: String) extends RuntimeException(origRequest)
}

