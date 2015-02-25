package nest.sparkle.util

import java.util.concurrent.atomic.AtomicBoolean

import rx.lang.scala.subjects.PublishSubject
import rx.lang.scala.{Subscriber, Notification, Subject, Observable}

object ObservableUtil { // consider extending observable with these via implicit

  /** return two Observables from a source observable. One containing the first item, and the other
    *  containing the remaining items. */
  def headTail[T](observable: Observable[T]): (Observable[T], Observable[T]) = {
    val buffer = observable.cache(2)

    (headSafe(buffer), buffer.drop(1))
  }

  /** like head, but returns a an empty observable if there is no head */
  def headSafe[T](observable:Observable[T]):Observable[T] = {
    observable.headOption.flatMap { optValue =>
      optValue match {
        case Some(value) => Observable.from(Seq(value))
        case None        => Observable.empty
      }
    }
  }

  /** like Observable.reduce, but returns an empty observable when passed an empty observable
    * instead of throwing */
  def reduceSafe[T](observable:Observable[T])(fn: (T, T) => T):Observable[T] = {
    val opts = observable.map(Some(_)) :+ None
    val reducedOpts =
      opts.reduce { (optA, optB) =>
        (optA, optB) match {
          case (Some(a), Some(b)) => Some(fn(a, b))
          case (Some(a), None)    => Some(a)
          case (None, Some(b))    => Some(b)
          case (None, None)       => None
        }
      }

    reducedOpts.flatMap { option => Observable.from(option)}
  }

  /** (for debugging) return an observable chained from the provided observable that
    * prints activity to the console. Tracked activities include both data flow
    * and subscription flow: onSubscribe, onNext, onCompleted
    * and onError. (Backpressure info is not currently tracked.) */
  def debugTapped[T](observable:Observable[T], name:String): Observable[T] = {
    observable
      .doOnSubscribe{ println(s"$name: onSubscribe")}
      .doOnNext{ elem => println(s"$name: onNext $elem")}
      .doOnCompleted { println(s"$name: onCompleted")}
      .doOnError{ err => println(s"$name: onError $err")}
  }

}