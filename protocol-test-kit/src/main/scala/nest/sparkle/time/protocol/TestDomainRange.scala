package nest.sparkle.time.protocol

import nest.sparkle.store.Event

/** utility for testing the DomainRange transform */
object TestDomainRange {
  /** return the min max of domain and range of a list of events */
  def minMaxEvents(events: Seq[Event[Long, Double]]): (Long, Long, Double, Double) = {
    val domainMin = events.map { case Event(k, v) => k }.min
    val domainMax = events.map { case Event(k, v) => k }.max
    val rangeMin = events.map { case Event(k, v) => v }.min
    val rangeMax = events.map { case Event(k, v) => v }.max

    (domainMin, domainMax, rangeMin, rangeMax)
  }

}
