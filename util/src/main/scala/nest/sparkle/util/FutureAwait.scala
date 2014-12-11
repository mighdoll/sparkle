package nest.sparkle.util

import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._


/** extend Future with an .await method */
object FutureAwait {
  object Implicits {
    /** extends Future with an await method */
    implicit class FutureWithAwait[T](future:Future[T]) {
      /** Return the result of the future, or throw a TimeoutException if the future does not complete within the duration.
       *  
       *  Normally callers should use future combinators like map, flatMap and foreach - await will block a thread. 
       *  Blocking a thread is convenient in some cases, notably unit/integration tests.
       *  
       *  Duration is an implicit parameter so that callers can call .await without a suffix. As a matter of code style, 
       *  callers who swish to pass a custom duration will typically pass a duration explicitly (and not simply make 
       *  an implicit duration available in the lexical context). 
       */
      def await(implicit duration:Duration = 5.seconds):T = {
        Await.result(future, duration)
      }
    }
    
  }
}