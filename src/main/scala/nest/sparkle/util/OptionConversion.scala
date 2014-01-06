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

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.control.ControlThrowable
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/** import this to enrich Option with a .toFuture method */
object OptionConversion {
  implicit class OptionFuture[T](opt: Option[T]) {

    /** convert this Option to a Future, failing with the provided Throwable
      * if the Option is empty.
      */
    def futureOrFailed(failure: => Throwable): Future[T] = {
      val promise = opt match {
        case Some(x) => Promise.successful(x)
        case None    => Promise.failed(failure)
      }
      promise.future
    }

    /** convert this Option to a Future, failing with the provided Throwable
      * if the Option is empty.
      */
    def toFutureOr(failure: => Throwable): Future[T] = {
      val promise = opt match {
        case Some(x) => Promise.successful(x)
        case None    => Promise.failed(failure)
      }
      promise.future
    }

    /** convert this option to a Try, failing with the provided Throwable
      * if the Option is empty
      */
    def toTryOr(failure: => Throwable): Try[T] = {
      opt match {
        case Some(x) => Success(x)
        case None    => Failure(failure)
      }
    }

  }

}
