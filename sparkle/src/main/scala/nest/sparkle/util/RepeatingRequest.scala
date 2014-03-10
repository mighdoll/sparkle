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

package nest.sparkle.util
import spray.http._
import spray.client.pipelining._
import scala.concurrent.Future
import akka.actor.ActorRefFactory
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import akka.actor.ActorSystem
import scala.concurrent.duration._
import scala.util.Success
import spray.util._
import scala.util.Failure
import java.util.concurrent.TimeoutException

object RepeatingRequest {
  def get(uri: String, maxAttempts: Int = 4)(implicit refFactory: ActorRefFactory,
                                             executionContext: ExecutionContext,
                                             futureTimeout: Timeout = 5.seconds): Future[String] = {
    val pipeline = sendReceive

    retryingFuture(pipeline(Get(uri))) map {_.entity.asString}
  }

  /** Call a function that returns a future.  If the future times out, try again (repeating up to maxAttempts times). */
  def retryingFuture[T](fn: => Future[T], maxAttempts: Int = 4)(implicit executionContext: ExecutionContext,
                                                                futureTimeout: Timeout = 10.seconds):Future[T] = {
    def retry(attemptsRemaining: Int): Future[T] = {
      if (attemptsRemaining <= 0) {
        Future.failed(new TimeoutException(s"Timeout after $maxAttempts attempts"))
      } else {
        fn recoverWith {
          case t: TimeoutException => 
            Console.println("retryingFuture retrying.  Remaining attempts: $attemptsRemaining")
            retry(attemptsRemaining - 1)
        }
      }
    }

    retry(maxAttempts)
  }
}
