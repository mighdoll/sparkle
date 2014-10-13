package nest.sparkle.http

import spray.http.{HttpMessage, HttpRequest, HttpResponse}
import spray.routing.Directive.SingleValueModifiers
import spray.routing.{Directive0, Directive1, Directives}

import nest.sparkle.util.Log

/** Mix in to a Directives route structure to add the withRequestResponseLog directive.
  * Wrap a route in the logwithRequestResponseLog directive and it will log requests and
  * responses to the debug log, with full detail provided at the if TRACE level is enabled.
  */
trait HttpLogging {
  self: Directives with Log =>

  // report the client's ip address, or "unknown" if the ip address isn't known
  lazy val sourceIP: Directive1[String] = clientIP.map(_.toString) | provide("unknown-ip")

  lazy val withRequestResponseLog: Directive0 = {
    /** return a trace loggable string containing full headers and the start of the entity body */
    def headersAndBody(message: HttpMessage): String = {
      val headers = message.headers.map(_.toString).mkString("  ", "\n  ", "\n")
      val body = message.entity.asString.take(300)
      headers + body
    }

    /** extract the request and client ip address from the request */
    val requestIp =
      for {
        ip <- sourceIP
        request <- extract(_.request)
      } yield {
        (request, ip)
      }

    /** log the http request */
    def logRequestStart(requestLine: String, request: HttpRequest) {
      val headersAndBodyLog = headersAndBody(request)
      log.trace(s"$requestLine\n$headersAndBodyLog")
    }

    /** log the http response */
    def logRequestComplete(ip: String, requestLine: String, request: HttpRequest, response: HttpResponse) {
      val responseCode = response.status.intValue.toString
      log.info(s"$requestLine $responseCode")
      log.trace(s"$ip $responseCode\n  ${headersAndBody(response)}")
    }

    /** return a log message string for an http request */
    def requestLogLine(request: HttpRequest, ip: String): String = {
      val uri = request.uri
      val method = request.method.name
      s"$ip: $method $uri"
    }

    // log before and after the request completes
    requestIp.flatMap {
      case (request, ip) =>
        val requestLog = requestLogLine(request, ip)
        logRequestStart(requestLog, request)
        mapHttpResponse{ response =>
          logRequestComplete(ip, requestLog, request, response)
          response
        }
    }

  }

}