package nest.sparkle.util

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.util.Timeout
import java.util.concurrent.TimeoutException

/** Utility to repeatedly call a function that returns a future. */
object RetryingFuture extends Log{

  /** Call a function that returns a future.  
   *  If the future times out, try again (repeating up to maxAttempts times). */
  def retryingFuture[T] // format: OFF
      ( fn: => Future[T], maxAttempts: Int = 4 )
      ( implicit executionContext: ExecutionContext, futureTimeout: Timeout = 10.seconds)
      : Future[T] = { // format: ON

    def retry(attemptsRemaining: Int): Future[T] = {
      if (attemptsRemaining <= 0) {
        Future.failed(new TimeoutException(s"Timeout after $maxAttempts attempts"))
      } else {
        fn recoverWith {
          case t: TimeoutException =>
            log.info(s"retryingFuture retrying.  Remaining attempts: $attemptsRemaining")
            retry(attemptsRemaining - 1)
        }
      }
    }

    retry(maxAttempts)
  }

}