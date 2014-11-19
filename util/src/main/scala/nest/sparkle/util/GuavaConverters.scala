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

import com.google.common.util.concurrent.{Futures => GuavaFutures, ListenableFuture, FutureCallback}
import scala.concurrent.{Future, Promise}

object GuavaConverters {
  implicit class GuavaFutureConverter[T](val guavaFuture: ListenableFuture[T]) extends AnyVal {

    /** return a scala.concurrent future for this anyval */
    def toFuture: Future[T] = {
      val promise = Promise[T]()

      // we do a bit of casting back and forth from Any, otherwise we can't compile as
      // a value class in 2.10.x.  This is fixed in scala 2.11

      val callBack = new FutureCallback[Any] {
        def onSuccess(result: Any): Unit = promise.success(result.asInstanceOf[T])

        def onFailure(t: Throwable): Unit = promise.failure(t)
      }

      GuavaFutures.addCallback(guavaFuture, callBack.asInstanceOf[FutureCallback[T]])
      promise.future
    }
  }

}

///** example of scala 2.10.x compilation issue */
//object FailsOn2_10_ButWorksOn2_11 {
//  trait Inter[B] {
//    def inter(param: B): Unit
//  }
//
//  implicit class Foo[T](val interface: Inter[T]) extends AnyVal {
//    def foo() {
//      new Inter[T] {
//        def inter(param: T): Unit = ???
//      }
//    }
//  }
//
//}
