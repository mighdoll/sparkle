package nest.sparkle.http

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import spray.routing._

import nest.sparkle.measure.StartedSpan

/** Directives to add timing measurements to routes */
trait TimingDirectives
{ self: HttpService =>
  
  implicit def executionContext: ExecutionContext
  
  /** Time a future that returns a response for a route */
  def timedOnComplete[T](future: Future[T])(fn: Try[T] => Route)
      (implicit span: StartedSpan): Route = {
    onComplete(future) { result =>
      span.complete()
      fn(result)
    }
  }

}
