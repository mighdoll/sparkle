///* Copyright 2014  Nest Labs
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.  */
//
//package nest.sparkle.loader.kafka
//
//import org.scalatest.FunSuite
//import org.scalatest.Matchers
//import rx.lang.scala.Observable
//import rx.lang.scala.Observer
//import scala.concurrent.Future
//import scala.concurrent.ExecutionContext
//import rx.lang.scala.Subscription
//import nest.sparkle.util.ObservableFuture._
//import spray.util._
//
///** Demonstrate how Observable backpressure works:
// *  . The producing thread blocks if the consumer is busy.
// *  . The producer *must* respect cancellation and not produce more elements than the consumer wants
// *  . Requesting the next element from the source needs to be thread synchronous with cancellation
// */
//class TestObservable extends FunSuite with Matchers {
//
//  test("iterator to observable") {
//    Thread.currentThread().setName("consumer")
//    val observable = Observable.create { observer: Observer[Int] =>
//      import ExecutionContext.Implicits.global
//      var cancelled = false
//      Future {
//        Thread.currentThread().setName("producer")
//        val iterator = Iterator.from(1)
//        iterator.takeWhile { value =>
//          println(value)
//          observer.onNext(value)
//          !cancelled
//          // uncomment to see what happens if the producer produces more elements beyond cancellation
//          // true
//        }.foreach{ _ => }
//      }
//      Subscription { cancelled = true }
//    }
//
//    val result1 = observable.map {value =>
//      Thread.sleep(250) // pretend we're doing some work
//      value
//    }.take(10).toFutureSeq.await
//
//    Thread.sleep(1000000)
//  }
//}
//
