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

}