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

import java.io.FileNotFoundException
import java.nio.file.NoSuchFileException
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import akka.event.Logging.InfoLevel
import spray.http.{ HttpRequest, HttpResponse, HttpResponsePart, StatusCodes }
import spray.http.CacheDirectives._
import spray.http.HttpHeaders._
import spray.http.StatusCodes._
import spray.httpx.marshalling.Marshaller
import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable
import spray.routing.HttpService
import spray.routing.Route
import spray.routing.Directives
import spray.routing.directives.LogEntry
import nest.sparkle.legacy.ColumnNotFoundException
import nest.sparkle.legacy.DataRegistry
import spray.routing.Directive.pimpApply
import spray.routing.directives.LoggingMagnet.forRequestResponseFromHttpResponsePartShow
import spray.routing.directives.OnCompleteFutureMagnet.apply
import nest.sparkle.legacy.DataServiceV0
import nest.sparkle.time.protocol.DataServiceV1
import nest.sparkle.util.Log
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths

/** http API for serving data and static web content */
trait DataService extends HttpService with DataServiceV0 with DataServiceV1 with Log {
  def registry: DataRegistry
  /** Subclasses set this to the default web page to display (e.g. the dashboard) */
  def webRoot: Option[String] = None

  /** Subsclasses can override customRoutes to provide additional routes.  The resulting
    * routes have priority and can replace any built in routes.
    */
  def customRoutes: Iterable[Route] = List()

  private def externalRoutes: Route = customRoutes.reduceLeftOption{ (a, b) => a ~ b } getOrElse reject()

  implicit def executionContext: ExecutionContext

  val staticResponse = { // static data from the resource folder: don't cache, this is for developers
    respondWithHeaders(
      `Cache-Control`(`no-cache`, `no-store`, `must-revalidate`),
      RawHeader("Expires", "0"),
      RawHeader("Pragma", "no-cache")
    ) {
        getFromResourceDirectory("web")
      }
  }

  val rootPath: Route = { // static data from the template folder (if provided)
    webRoot.map { path =>
      getFromDirectory(path)
    } getOrElse {
      reject
    }
  }

  val getIndex: Route = { // return index.html from custom folder if provided, otherwise use built in default page
    webRoot.map { path =>
      getFromDirectory(path + "/index.html")
    } getOrElse {
      getFromResource("web/index.html")
    }
  }

  val healthRequest: Route = {
    path("health") {
      dynamic {
        val registryRequest = registry.allSets().map(_ => "ok")
        complete(registryRequest)
      }
    }
  }

  /** all api endpoints */
  val route = {  // format: OFF    
    logRequestResponse(showResponse _) { 
      externalRoutes ~
      v1protocol ~
      get {
        (path("") | path ("/")) { 
          getIndex
        } ~
        healthRequest ~
        v0protocol ~
        rootPath ~
        staticResponse ~
        unmatchedPath { path => complete(NotFound, "not found") }
      }
    } 
  } // format: ON

  def showResponse(request: HttpRequest): HttpResponsePart => Option[LogEntry] = {
    case HttpResponse(OK, _, _, _)         => Some(LogEntry(" 200: " + request.uri, InfoLevel))
    case HttpResponse(NotFound, _, _, _)   => Some(LogEntry(" 404: " + request.uri))
    case response@HttpResponse(_, _, _, _) => Some(LogEntry(response + " request=" + request.uri))
    case x                                 => Some(LogEntry("unexpected error: " + x + " request=" + request.uri))
  }
}

trait RichComplete extends Directives {
  implicit def executionContext: ExecutionContext
  def richComplete[T](future: Future[T])(implicit marshaller: Marshaller[T]): Route = {
    onComplete(future) {
      _ match {
        case Success(s) =>
          complete(s)
        case Failure(notFound: NoSuchFileException) =>
          complete(StatusCodes.NotFound -> notFound.getMessage)
        case Failure(notFound: FileNotFoundException) =>
          complete(StatusCodes.NotFound -> notFound.getMessage)
        case Failure(notFound: ColumnNotFoundException) =>
          complete(StatusCodes.NotFound -> notFound.getMessage)
        case Failure(x) =>
          complete(x)
      }
    }
  }

}
