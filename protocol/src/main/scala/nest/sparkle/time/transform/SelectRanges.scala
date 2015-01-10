package nest.sparkle.time.transform

import rx.lang.scala.Observable
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.store.Event
import nest.sparkle.store.Column
import scala.concurrent.ExecutionContext
import nest.sparkle.util.Exceptions.NYI
import nest.sparkle.store.OngoingEvents
import scala.concurrent.Future
import spire.math.Numeric
import spire.implicits._
import scala.reflect.runtime.universe._
import nest.sparkle.util.KindCast.castKind
import nest.sparkle.time.transform.ItemStreamTypes._
import nest.sparkle.measure.Span


/** support for fetching ranges of column data */
object SelectRanges {
  
  /** read mulitple ranges from a column */
  def fetchRanges[T, U] // format: OFF
      (column: Column[T, U], ranges: Option[Seq[RangeInterval[T]]], parentSpan:Option[Span] = None) 
      (implicit execution:ExecutionContext):Seq[IntervalAndEvents[T,U]] = { // format: ON

    def readAllIntervals(intervals: Seq[RangeInterval[T]]): Seq[IntervalAndEvents[T, U]] = {
      intervals.map { interval =>
        val events = column.readRange(interval.start, interval.until, interval.limit, parentSpan)
        IntervalAndEvents(Some(interval), events)
      }
    }

    ranges match {
      case None            => Seq(IntervalAndEvents(None, column.readRange(None, None, None, parentSpan)))
      case Some(intervals) => readAllIntervals(intervals)
    }
  }

  /** read a single range from a column (or the entire column if no range is specified) */
  def fetchRange[K]( // format: OFF
      column: Column[K, Any], 
      optRange: Option[RangeInterval[K]] = None,
      parentSpan: Option[Span]) 
      (implicit execution:ExecutionContext):RawItemStream[K] = { // format: ON

    val ongoingEvents =
      optRange.map { range =>
        column.readRange(range.start, range.until, range.limit, parentSpan)
      }.getOrElse {
        column.readRange(None, None, None, parentSpan)
      }
      
    val keyType: TypeTag[K] = castKind(column.keyType)
    val valueType: TypeTag[Any] = castKind(column.valueType)
    new RawItemStream(ongoingEvents.initial, ongoingEvents.ongoing, optRange)(keyType, valueType)
  }

}

/** Enables extending one or more RangeIntervals by a fixed amount. This is handy
 *  for transforms that need to e.g. read an extra day earlier to catch
 *  interval start dates.
 */
case class ExtendRange[T:Numeric](before:Option[T] = None, after:Option[T]=None) {
  
  /** extend one or more RangeIntervals returning the extended RangeInterval*/
  def extend(ranges:Seq[RangeInterval[T]]):Seq[RangeInterval[T]] = {
      ranges.map { range =>
        val modifiedStart: Option[T] =
          for {
            start <- range.start
            startAdjust <- before
          } yield {
            start + startAdjust
          }

        val modifiedUntil: Option[T] =
          for {
            until <- range.until
            untilAdjust <- after
          } yield {
            until + untilAdjust
          }

        range.copy(start = modifiedStart, until = modifiedUntil)
      }
    
  }

}
