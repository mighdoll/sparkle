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

import scala.util.Try
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Failure
import java.util.concurrent.TimeoutException

/** add a .toFuture method on Try */
object TryToFuture {
  implicit class FutureTry[T](wrappedTry: Try[T]) {
    def toFuture: Future[T] = {
      wrappedTry match {
        case Success(result)    => Future.successful(result)
        case Failure(exception) => Future.failed(exception)
      }
    }
  }
}

/** add a .toTry method on Future */
object FutureToTry {
  implicit class TryFuture[T](wrappedFuture: Future[T]) {
    def toTry()(implicit atMost: Duration = 30.seconds): Try[T] = {
      val readyFuture = Await.ready(wrappedFuture, atMost)
      readyFuture.value match {
        case Some(tried) => tried
        case None        => Failure(new TimeoutException)
      }
    }
  }

}