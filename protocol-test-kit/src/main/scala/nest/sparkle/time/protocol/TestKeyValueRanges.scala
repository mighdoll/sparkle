package nest.sparkle.time.protocol

import nest.sparkle.store.Event

/** utility for testing the KeyValueRanges transform */
object TestKeyValueRanges {
  /** return the min max of the keys and values of a list of events */
  def minMaxEvents(events: Seq[Event[Long, Double]]): (Long, Long, Double, Double) = {
    val keyMin = events.map { case Event(k, v) => k }.min
    val keyMax = events.map { case Event(k, v) => k }.max
    val valueMin = events.map { case Event(k, v) => v }.min
    val valueMax = events.map { case Event(k, v) => v }.max

    (keyMin, keyMax, valueMin, valueMax)
  }

}
