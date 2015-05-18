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
import akka.actor.ActorSystem
import com.typesafe.config.Config
import spray.routing._
import spray.http.{StringRendering, StatusCodes}
import spray.json._
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.{EntityNotFoundForLookupKey, Store}
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.protocol.ResponseJson.{ StreamsMessageFormat, StatusMessageFormat }
import nest.sparkle.time.server.RichComplete
import nest.sparkle.util.Log
import nest.sparkle.measure.{Span, TraceId, Measurements}
import nest.sparkle.time.protocol.ProtocolError.streamErrorMessage

/** Provides the v1 sparkle data api
  */
trait DataServiceV1 extends Directives with RichComplete with CorsDirective with Log {
  def actorSystem: ActorSystem
  implicit def executionContext: ExecutionContext // TODO can we just use actorSystem.dispatcher here?
  def store: Store
  def rootConfig: Config
  implicit def measurements:Measurements

  // (lazy to help test logging initialization order)
  lazy val api = StreamRequestApi(store, rootConfig)(actorSystem, measurements)

  lazy val v1protocol = {
    cors {
      pathPrefix("v1") { // format: OFF
        postDataRequest ~
        columnsRequest ~
        entitiesRequest
      } // format: ON
    }
  }

  private lazy val postDataRequest:Route =
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

  /** Returns the given entity path's columns */
  private lazy val columnsRequest:Route =
    pathPrefix("columns") {
      path(Segments) { entityPath =>
        if (entityPath.isEmpty) {
          complete(StatusCodes.NotFound -> "Entity path not specified")
        } else {
          val pathString = entityPath.mkString("/")
          val futureColumnNames = store.entityColumnPaths(pathString)
          richComplete(futureColumnNames)
        }
      }
    }

  /** Returns the entity paths associated with the given lookup key */
  private lazy val entitiesRequest:Route =
    path("findEntity" ) {
      parameter('q) { term =>
        if (term.isEmpty) {
          complete(StatusCodes.NotFound -> "Lookup key not specified")
        } else {
          val futureNames = store.entities(term).recover {
              case e:EntityNotFoundForLookupKey => Seq()
            }
          richComplete(futureNames)
        }
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
    val traceId = request.traceId.map(TraceId(_)).getOrElse(TraceId.create())
    val span = Span.startRoot("data-request-http", traceId)
    val futureResponse = httpDataRequest(request)
    futureResponse.onComplete {
      case Success(response: StreamsMessage) =>
        span.complete()
        ctx.complete(response)
      case Failure(err) =>
        val errSpan = span.copy(name = "data-request-http-error")
        errSpan.complete()
        ctx.complete(streamErrorMessage(request, err))
    }
  }

  /** return a StreamRequestMessage if it can be succesfully parsed from a string */
  private def parseStreamRequest(request: String): Try[StreamRequestMessage] = {
    val result =
      for {
        json <- nonFatalCatch.withTry { request.parseJson }
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


  /** error with no parsed request available */
  private def completeWithError(context: RequestContext, status: Status): Unit = {
    log.warn(s"StreamError $status")
    val statusMessage = StatusMessage(requestId = None, realm = None, traceId = None,
      messageType = MessageType.Status, message = status)
    context.complete(statusMessage)
  }

  case class RequestParsingException(origRequest: String) extends RuntimeException(origRequest)
}

