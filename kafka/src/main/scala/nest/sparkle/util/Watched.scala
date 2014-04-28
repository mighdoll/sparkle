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

import scala.collection.mutable
import scala.concurrent.duration.{ Duration, DurationInt }
import rx.lang.scala.{ Observer, Subscription }
import rx.lang.scala.schedulers.TrampolineScheduler
import rx.lang.scala.Subject
import rx.lang.scala.Observable

/** Manages a set of filters watching an Observable stream of source data. Clients register
  * a filter by calling watch() on a Watch object. Results matching the filter are reported
  * to an Observable in the Watch object.
  */
trait Watched[T] extends Log {
  // raw data stream that we for watchers
  private val publishedData = PublishSubject[T]()

  // currently active watches
  private lazy val watches = mutable.Set[Watch[T]]()

  /** subclasses should publish data events to this stream */
  protected def watchedData: Observer[T] = publishedData

  // The idea here is to use a single executor for both the subscribe requests and the data
  // pipeline to provide mutual exclusion. It doesn't work though..
  private val trampolineScheduler = TrampolineScheduler()


  // TODO fix me based on reviewer feedback
  publishedData.subscribeOn(trampolineScheduler).subscribe(processEvent _, handleError _, complete _)


  /** Events sent over the data stream and matching the watch's filter will
    * be sent to the watch's Observerable report stream.  */
  def watch(watch: Watch[T]): Unit = {
    // RX how ought this work?
    //    trampolineScheduler.schedule {inner =>
    //      processWatch(watch)
    //    }
    // TODO implement mutual exclusion scheme between watches and event processing
    processWatch(watch)
  }


  /** client filtering request */
  private def processWatch(watch: Watch[T]) {
    watches += watch
    watch.fullReport.onNext(WatchStarted())
  }

  /** send new event on the data stream to any matching filters */
  private def processEvent(event: T) {
    validSubscriptions().foreach { subscribe =>
      subscribe.filter match {
        case Some(filter) if !filter(event) => // skip, filter didn't match
        case _                              =>
          log.trace(s"processEvent: $event")
          subscribe.fullReport.onNext(WatchData(event))
      }
    }
  }

  /** source data stream had an error, notify watchers */
  private def handleError(error: Throwable) {
    validSubscriptions().foreach { _.fullReport.onError(error) }
  }

  /** source data stream is finished, notify watchers */
  private def complete() {
    validSubscriptions().foreach { _.fullReport.onCompleted() }
  }

  /** delete any expired or cancelled watches, return the currently valid ones */
  private def validSubscriptions(): mutable.Set[Watch[T]] = {
    val deadSet = watches.filter(_.subscription.isUnsubscribed)

    val now = System.currentTimeMillis()
    val oldSet = watches.filter { _.expires <= now }

    (deadSet ++ oldSet).foreach { done =>
      done.fullReport.onCompleted()
      watches.remove(done)
    }
    watches
  }
}

/** An element on the filtered fullReport stream from a Watch: either Data or Startup */
sealed trait WatchReport[T]
case class WatchStarted[T]() extends WatchReport[T]
case class WatchData[T](val data: T) extends WatchReport[T]

/** A single filter on the data stream */
case class Watch[T](
    /** report matching events to this Observer */
    val fullReport: Subject[WatchReport[T]] = PublishSubject[WatchReport[T]],

    /** filter will last this long */
    val duration: Duration = 5.minutes,

    /** match only filtered events. match all events */
    val filter: Option[T => Boolean] = None) extends Log {

  /** expiration of this subscription in epoch milliseconds */
  val expires: Long = System.currentTimeMillis() + duration.toMillis

  /** clients can cancel the watch by calling unsubscribe */
  val subscription = Subscription()

  /** just the data, w/o Report[] wrapper */
  lazy val report: Observable[T] = {
    // RX Observable.collect would be handy here
    fullReport.flatMap { watchReport =>
      log.trace(s"report: $watchReport")    // TODO why is this received twice?
      watchReport match {
        case WatchData(data)          => Observable.from(List(data))
        case started: WatchStarted[T] => Observable.empty
      }
    }
  }
}


