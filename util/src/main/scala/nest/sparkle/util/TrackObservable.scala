package nest.sparkle.util

// TODO move to util .jar
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

import rx.lang.scala.Observable

/** a utility class for tracking when a set of Observables are complete */
case class TrackObservable() {
  private val futureFinished = mutable.ArrayBuffer[Future[Unit]]()
  
  /** patch an observable to record when it completes */
  def finish[T](observable: Observable[T]): Observable[T] = {
    val promise = Promise[Unit]()
    futureFinished += promise.future
    // (Note: we'll risk a runtime error if we trigger an additional subscription to the stream, so we tap into the existing one.)
    observable.doOnCompleted {
      promise.complete(Success(()))
    }
  }
  
  /** patch an observable to record when it starts */
  def start[T](observable:Observable[T]):Observable[T] = {
    val promise = Promise[Unit]()
    futureFinished += promise.future
    // (Note: we'll risk a runtime error if we trigger an additional subscription to the stream, so we tap into the existing one.)
    observable.doOnEach{ _ => 
      if (!promise.isCompleted) {
        promise.complete(Success(()))
      }
    }
  }

  def allFinished()(implicit execution: ExecutionContext): Future[Unit] =
    Future.sequence(futureFinished).map(_ => ())

}