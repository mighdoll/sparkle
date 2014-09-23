package nest.sparkle.util

import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

import org.joda.time.Duration

import com.github.nscala_time.time.Implicits._

/**
 * Class to provide exponentially increasing with limit sleep time.
 */
case class RetryManager(initial: Duration, limit: Duration)
{  
  /**
   * Execute the function fn ignoring all non-fatal exceptions until the function succeeds.
   * Time between attempts starts at initial and goes until
   * the limit which is repeated indefinitely.
   */
  def execute[T](fn: => T): T = {
    
    @annotation.tailrec
    def attempt(sleepTime: Duration): T = {
      def tryRun: Try[T] = {
        try {
          Success(fn)
        } catch {
          case NonFatal(err) => Failure(err)
        }
      }
      
      tryRun match {
        case Success(result) => result
        case Failure(err)    =>
          val newSleep = sleep(sleepTime)
          attempt(newSleep)
      }
    }
    
    attempt(initial)
  }
  /**
   * Execute the function fn ignoring specified exceptions until the function succeeds
   * or throws an unhandled exception. Time between attempts starts at initial and goes until
   * the limit which is repeated indefinitely.
   * 
   * TODO: Accept a list of Exceptions to ignore.
   */
/*
  def execute[T, E <% Exception](fn: => T): Try[T] = {
    
    @annotation.tailrec
    def attempt(sleepTime: Duration): Try[T] = {
      def tryRun: Try[T] = {
        try {
          Success(fn)
        } catch {
          case NonFatal(err) => Failure(err)
        }
      }
      
      tryRun match {
        case Failure(err: E) =>  // This fails to compile due to type erasure
          val newSleep = sleep(sleepTime)
          attempt(newSleep)
        case _               => tryRun
      }
    }
    
    attempt(initial)
  }
*/
  
  /** Make the current thread sleep for the current duration then compute the next duration */
  private def sleep(sleepTime: Duration): Duration = {
    Thread.sleep(sleepTime.millis)
    
    val nextSleepTime = sleepTime + sleepTime
    nextSleepTime match {
      case t if t >= limit => limit
      case t               => t
    }
  }
}
