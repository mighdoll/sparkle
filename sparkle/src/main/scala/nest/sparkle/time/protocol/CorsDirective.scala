/* Copyright 2014  Nest Labs

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

import spray.routing.Directives
import spray.http.AllOrigins
import spray.http.SomeOrigins
import spray.http.HttpOrigin
import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.HttpHeader
import spray.routing.Directive1
import spray.routing.Route
import spray.http.HttpMethod

/** Mix in to a Directives route structure to add the cors directive. Wrap a route in the cors
  * directive, and route will support OPTIONS preflight requests and cors header generation.
  * Mixers must override corsHosts to enable cors for the appropriate domains.
  */
trait CorsDirective {
  self: Directives =>
  /** allow browsers from these other domains to access the endpoint.
    * "*" means any domain, ("*" is sometimes handy for internal development but is generally
    * ill-advised on public servers for security reasons.)
    */
  def corsHosts: List[String] = List()

  /** override to change the list of http methods allowed for CORS requests */
  lazy val corsMethods: List[HttpMethod] = List(GET, POST)

  /** override to change the list of http headers allowed for CORS requests */
  def corsHeaders: List[String] = {
    val headerStrings = corsBaseHeaders.map(_.name)
    (headerStrings ::: corsAdditionalHeaders)
  }

  private lazy val corsBaseHeaders: List[ModeledCompanion] = List(`Content-Type`, Origin, Accept,
    `Accept-Encoding`, `Accept-Language`, Host, `User-Agent`, `Authorization`, `Cache-Control`)

  private lazy val corsAdditionalHeaders: List[String] = List("Referer", "X-Requested-With", "DNT", "Keep-Alive") // SPRAY why no Referer?
  
  private lazy val corsHeaderString = corsHeaders.mkString(", ")
  
  /** all allowable methods, including OPTIONS which is always allowable if CORS is enabled */
  private lazy val allCorsMethods = OPTIONS :: corsMethods

  /** Add CORS processing to an inner route, by including CORS headers in
    * responses to requests matching that route and by handling OPTIONS
    * preflight requests.
    */
  def cors(inner: Route): Route = {
    options {
      corsWrap {
        complete("")
      }
    } ~
      corsWrap {
        inner
      }
  }

  /** convert a route to a route that responds with CORS headers
    * if a valid origin header is presented by the requestor
    */
  private def corsWrap(inner: Route): Route = {
    corsOriginMatch { origin =>
      respondWithHeaders(corsHeaders(origin)) {
        inner
      }
    } ~
      inner
  }

  /** a directive that matches the client requests Origin header based
    * on the corsHosts setting (which controls which hosts if any are
    * permitted to cross origin access)
    */
  private lazy val corsOriginMatch: Directive1[String] = {
    corsHosts match {
      case Nil                          => reject
      case _ if corsHosts.contains("*") => headerValuePF(extractOrigin)
      case _                            => headerValuePF(extractMatchingOrigin)
    }
  }

  /** a list of headers to return for a successful CORS request */
  private def corsHeaders(requestOrigin: String): List[HttpHeader] = {
    List(
      `Access-Control-Allow-Origin`(SomeOrigins(List(requestOrigin))),
      `Access-Control-Allow-Methods`(corsMethods),
      `Access-Control-Allow-Headers`(corsHeaderString)
    )
  }

  /** (for use in a headerValuePF directive) extracts the request origin if
    * it is on the list of approved cross origin hosts
    */
  private def extractMatchingOrigin: PartialFunction[HttpHeader, String] = {
    case `Origin`(requestOrigin :: tail) if corsHosts.contains(requestOrigin.toString) =>
      val result = requestOrigin.toString
      result
  }

  /** (for use in a headerValuePF directive) extracts the request origin unconditionally
    * (e.g. for a "*" setting to allow any host)
    */
  private def extractOrigin: PartialFunction[HttpHeader, String] = {
    case `Origin`(requestOrigin :: tail) =>
      val result = requestOrigin.toString
      result
  }

}
