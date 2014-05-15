package nest.sparkle.time.transform

import rx.lang.scala.Observable
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.store.Event
import nest.sparkle.store.Column
import scala.concurrent.ExecutionContext
import nest.sparkle.util.Exceptions.NYI

case class IntervalAndEvents[T, U](interval: Option[RangeInterval[T]], events: Observable[Event[T, U]])
object SelectRanges {
  def fetchRanges[T, U](column: Column[T, U], ranges: Option[Seq[RangeInterval[T]]]) // format: OFF
      (implicit execution:ExecutionContext):Seq[IntervalAndEvents[T,U]] = { // format: ON

    def readAllIntervals(intervals: Seq[RangeInterval[T]]): Seq[IntervalAndEvents[T, U]] = {
      intervals.map { interval =>
        val events =
          (interval.start, interval.until) match {
            // TODO should the column interface deal with half open ranges and limit itself?
            case (start@Some(_), until@Some(_)) => column.readRange(start, until)
            case (None, None)                   => column.readRange(None, None)
            case _                              => column.readRange(None, None) // TODO use column interface for faster/limit based reading
          }
        IntervalAndEvents(Some(interval), events)
      }
    }

    ranges match {
      case None            => Seq(IntervalAndEvents(None, column.readRange(None, None)))
      case Some(intervals) => readAllIntervals(intervals)
    }
  }
}