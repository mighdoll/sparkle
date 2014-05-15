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

import spray.http.StatusCodes
import spray.http.StatusCodes.NotFound
import spray.httpx.marshalling.Marshaller
import spray.httpx.marshalling.ToResponseMarshallable.isMarshallable
import spray.routing.{ Directives, HttpService, Route }
import spray.routing.Directive.pimpApply
import spray.routing.directives.OnCompleteFutureMagnet.apply

import nest.sparkle.legacy.{ ColumnNotFoundException, DataRegistry, DataServiceV0 }
import nest.sparkle.store.DataSetNotFound
import nest.sparkle.time.protocol.{ DataServiceV1, HttpLogging }
import nest.sparkle.util.Log

sealed trait FileOrResourceLocation
case class FileLocation(location: String) extends FileOrResourceLocation
case class ResourceLocation(location: String) extends FileOrResourceLocation

/** http API for serving data and static web content */
trait DataService extends HttpService with DataServiceV0 with DataServiceV1 with HttpLogging with Log {
  def registry: DataRegistry

  /** Subclasses set this to the default web page to display (e.g. the dashboard) */
  def webRoot: Option[FileOrResourceLocation] = None

  /** Subsclasses can override customRoutes to provide additional routes.  The resulting
    * routes have priority and can replace any built in routes.
    */
  def customRoutes: Iterable[Route] = List()

  private def externalRoutes: Route = customRoutes.reduceLeftOption{ (a, b) => a ~ b } getOrElse reject()

  implicit def executionContext: ExecutionContext

  val staticBuiltIn = { // static data from web html/javascript files pre-bundled in the 'web' resource path
    getFromResourceDirectory("web")
  }

  val webRootPath: Route = { // static data from the web-root folder (if provided)
    webRoot.map {
      case FileLocation(path)     => getFromDirectory(path)
      case ResourceLocation(path) => getFromResourceDirectory(path)
    } getOrElse {
      reject
    }
  }

  val indexHtml: Route = { // return index.html from custom folder if provided, otherwise use built in default page
    pathSingleSlash {
      webRoot.map {
        case FileLocation(path)     => getFromFile(path + "/index.html")
        case ResourceLocation(path) => getFromResource(path + "/index.html")
      } getOrElse {
        getFromResource("web/index.html")
      }
    }
  }

  val health: Route = {
    path("health") {
      dynamic {
        val registryRequest = registry.allSets().map(_ => "ok")
        complete(registryRequest)
      }
    }
  }

  val notFound = {
    unmatchedPath { path => complete(NotFound, "not found") }
  }

  /** all api endpoints */
  val route = {  // format: OFF
    withRequestResponseLog {
      externalRoutes ~
      v1protocol ~
      get {
        indexHtml ~
        v0protocol ~
        webRootPath ~
        staticBuiltIn ~
        health ~
        notFound
      }
    }
  } // format: ON
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
        case Failure(notFound: DataSetNotFound) =>
          complete(StatusCodes.NotFound -> notFound.getMessage)
        case Failure(x) =>
          complete(x)
      }
    }
  }

}
