package nest.sparkle.time.transform

import nest.sparkle.store.Event
import spire.math.Numeric
import spire.implicits._
import nest.sparkle.util.Log
import IntervalItem._
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.{Interval => JodaInterval}
import com.github.nscala_time.time.Implicits._


/** A closed numeric interval [start,end). Supports intersection (project) and merging (combine).  */
class IntervalItem[T:Numeric](val start:T, val length:T) extends Event[T,T](start, length) with Log {
  def end:T = argument + value
  
  /** intersect this interval with a target interval, optionally return their intersection. */
  def project(target: IntervalItem[T]): Option[IntervalItem[T]] = {
    if (start >= target.end || end <= target.start) { // no overlap
      None
    } else if (start >= target.start && end < target.end) { // totally within target
      Some(this)
    } else if (start < target.start && end > target.start && end < target.end) { // starts before target, ends within
      Some(IntervalItem(target.start, end - target.start))
    } else if (start < target.start && end >= target.end) { // starts before target ends after
      Some(target)
    } else if (start >= target.start && start < target.end && end >= target.end) { // starts within, ends after
      Some(IntervalItem(start, target.end - start))
    } else {
      log.error(s"- no overlap, fall through case. ${this.toTimeString}  atop: ${target.toTimeString}")
      None
    }
  }
  
  
    /** for debugging, interpret the interval as milliseconds and print as hours*/
  def toTimeString:String = {
    def dateToTimeString(dateTime: DateTime): String = {
      dateTime.toString(ISODateTimeFormat.hourMinuteSecond.withZone(DateTimeZone.UTC))
    }
    
    dateToTimeString(new DateTime(start)) + "|" + dateToTimeString(new DateTime(end))
  }
}


/** Support for Intervalitem, including a factory for creating an IntervalItem from an event and a method
 *  for coalescing a collection of IntervalItems. */
object IntervalItem {
  def apply[T:Numeric](start:T, length:T):IntervalItem[T] =
    new IntervalItem(start, length)

  // SCALA how could we unapply here?
  
  /** Combine a collection of intervals, returning a non-overlapping set of intervals that
   *  match the coverage.  Assumes that the intervals are presented in sorted order. */
  def combine[T:Numeric](intervals: Seq[IntervalItem[T]]): Seq[IntervalItem[T]] = {
    
    /** return true if two intervals overlap. the two intervals must be in start-sorted order */
    def overlapNext[T:Numeric](current:IntervalItem[T], next:IntervalItem[T]):Boolean = {
      next.start <= current.end
    }
    
    /** combine two intervals that are known to overlap each other. The two intervals must be in start sorted order */
    def combineOverlapped[T:Numeric](current:IntervalItem[T], next:IntervalItem[T]):IntervalItem[T] = {
      IntervalItem(current.start, spire.math.max(current.end, next.end) - current.start)
    }

    /** State for incremental processing of combined intervals.
     *  
      * @current active interval, might still be combined with subsequent intervals
      * @emit complete interval ready to be output, won't be combined anymore
      */
    case class State(current: Option[IntervalItem[T]], emit: Option[IntervalItem[T]])

    val states = intervals.scanLeft(State(None, None)) { (state, event) =>
      (state, event) match {
        case (State(None, _), next) => // start event, but don't emit yet
          State(Some(event), None)
        case (State(Some(current), _), next) if overlapNext(current, next) => // combine, but don't emit yet
          State(Some(combineOverlapped(current, next)), None)
        case (State(Some(current), _), next) => // !overlap emit previous, start new one
          State(Some(event), Some(current))
      }
    }

    val initial = states.flatMap(_.emit)
    val last = states.lastOption.flatMap(_.current)
    initial ++ last
  }

  
  /** Return the intersection of a set of intervals with a joda Interval,
   *  interpreting the intervals as containg epoch milliseconds */
  def jodaMillisIntersections[T: Numeric]( // format: OFF
      intervals: Seq[IntervalItem[T]],
      jodaInterval: JodaInterval): Seq[IntervalItem[T]] = { // format: ON
    val start: T = Numeric[Long].toType[T](jodaInterval.start.millis)
    val end: T = Numeric[Long].toType[T](jodaInterval.end.millis)
    val period = IntervalItem[T](start, end - start)
    intervals.flatMap { interval => interval.project(period) }
  }


}