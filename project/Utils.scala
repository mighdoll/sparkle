import scala.util.Try
import scala.concurrent.duration._

object Utils {
  
  /** run a function in a background thread.  The thread terminates when the function completes. */
  def runInThread(f: => Unit): Thread = {
    val thread = new Thread() { override def run() = f }
    thread.start()
    thread
  }

  /** Repeat a test function until it returns successfully, blocking the thread
    * for a definable period after each failure, or until the maxWait is exceeded.
    * Returns the result of the test function call. */
  def retryBlocking[T](maxWait:Duration = 1.second, retryDelay: Duration = 100.millis) // format: OFF
      (fn: => Try[T]): Try[T] = { // format: ON
    val start = System.nanoTime()
    val finishBy = start + maxWait.toNanos
    def tooLate():Boolean = System.nanoTime() > finishBy

    var result = fn
    while (result.isFailure && !tooLate()) {
      result = fn
      if (result.isFailure) { Thread.sleep(retryDelay.toMillis) }
    }
    result
  }
  
}
