package nest.sparkle.util

import rx.lang.scala.Observable

object ObservableUtil {
  /** return two Observables from a source observable. One containing the first item, and the other
   *  containing the and remaining items. */
  def headTail[T](observable: Observable[T]): (Observable[T], Observable[T]) = {
    // TODO RX there must be a better way?
    class First(var first: Boolean = true)
    var isFirst = true
    val grouped = observable.groupBy { x =>
      val wasFirst = isFirst
      isFirst = false
      wasFirst
    }
    val groups = grouped.replay(2)
    groups.connect
    val head = groups.first.flatMap{ case (_, observable) => observable }.take(1) // TODO drop take()?
    val tail = groups.drop(1).flatMap { case (_, observable) => observable }
    (head, tail)
  }

  /** like Observable.reduce, but returns an empty observable when passed an empty observable
    * instead of throwing */
  def reduceSafe[T](observable:Observable[T])(fn: (T, T) => T):Observable[T] = {
    val opts = observable.map(Some(_)) :+ None
    val reducedOpts =
      opts.reduce { (optA, optB) =>
        (optA, optB) match {
          case (Some(a), Some(b)) => Some(fn(a, b))
          case (Some(a), None) => Some(a)
          case (None, Some(b)) => Some(b)
          case (None, None) => None
        }
      }

    reducedOpts.flatMap { option => Observable.from(option)}
  }


}