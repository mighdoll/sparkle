/* Copyright 2014  Nest Labs

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

import scala.concurrent.ExecutionContext
import rx.lang.scala.Observable
import scala.concurrent.Future
import rx.lang.scala.Observer
import rx.lang.scala.Subscription
import rx.lang.scala.Subscriber

object ObservableIterator {
  implicit class WrappedIterator[T](val iterator: Iterator[T]) extends AnyVal {
    /**
     * Convert an Iterator to an Observable by dedicating a thread to iteration.
     *
     * Note that this consumes a threadpool thread until the iterator completes or the
     * subscription is cancelled.
     */
    def toObservable(implicit executionContext: ExecutionContext): Observable[T] = {
      Observable { subscriber:Subscriber[T] =>
        executionContext.execute(new Runnable {
          def run() { // run in a background thread.
            iterator.takeWhile { value =>
              subscriber.onNext(value)
              !subscriber.isUnsubscribed
            }.foreach { _ => } // trigger iteration until cancelled
          }
        })

      }
    }
  }
}
