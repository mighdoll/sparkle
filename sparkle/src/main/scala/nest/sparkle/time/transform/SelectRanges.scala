package nest.sparkle.time.transform

import rx.lang.scala.Observable
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.store.Event
import nest.sparkle.store.Column
import scala.concurrent.ExecutionContext
import nest.sparkle.util.Exceptions.NYI
import nest.sparkle.store.OngoingEvents

case class IntervalAndEvents[T, U](interval: Option[RangeInterval[T]], events: OngoingEvents[T, U])
object SelectRanges {
  def fetchRanges[T, U](column: Column[T, U], ranges: Option[Seq[RangeInterval[T]]]) // format: OFF
      (implicit execution:ExecutionContext):Seq[IntervalAndEvents[T,U]] = { // format: ON

    def readAllIntervals(intervals: Seq[RangeInterval[T]]): Seq[IntervalAndEvents[T, U]] = {
      intervals.map { interval =>
        val events = column.readRange(interval.start, interval.until, interval.limit) 
        IntervalAndEvents(Some(interval), events)
      }
    }

    ranges match {
      case None            => Seq(IntervalAndEvents(None, column.readRange(None, None)))
      case Some(intervals) => readAllIntervals(intervals)
    }
  }
}