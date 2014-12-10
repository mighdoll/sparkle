package nest.sparkle.http

import spray.http.StatusCodes
import spray.httpx.marshalling.Marshaller

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import spray.routing._

import nest.sparkle.measure.StartedSpan
import nest.sparkle.util.Log

/** Directives to add timing measurements to routes */
trait TimingDirectives
{ self: HttpService with Log =>
  
  implicit def executionContext: ExecutionContext
  
  /** Time a future that returns a response for a route */
  def timedOnComplete[T]
    (future: Future[T])
    (fn: Try[T] => Route)
    (implicit span: StartedSpan): Route = 
  {
    onComplete(future) { result =>
      span.complete()
      fn(result)
    }
  }

  /**
   * Time the Future for a route and handle success || failure.
   * @param future Future for HTTP result
   * @param path URL of the request. Used to log error message if failure.
   * @param span Span for timing
   * @tparam T Result type
   * @return Route
   */
  def timedOnComplete[T](future: Future[T], path: String)
    (implicit span: StartedSpan, marshaller: Marshaller[T]): Route = {
    onComplete(future) { result =>
      span.complete()
      result match {
        case Success(s) => complete(s)
        case Failure(x) =>
          log.error(s"Unhandled Exception running $path", x)
          complete(StatusCodes.InternalServerError -> x.toString)
      }
    }
  }

}
