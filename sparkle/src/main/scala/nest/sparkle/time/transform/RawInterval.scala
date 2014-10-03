package nest.sparkle.time.transform

import nest.sparkle.store.Event
import spire.implicits._
import spire.math.Numeric
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import nest.sparkle.util.Log
import org.joda.time.{Interval => JodaInterval}
import com.github.nscala_time.time.Implicits._

/** A closed numeric interval (start,end). Supports intersection (project) and merging (combine).  */
case class RawInterval[T: Numeric](start: T, end: T) extends Log {

  private val numeric = implicitly[Numeric[T]]

  /** length of the interval */
  def length:T = end - start

  /** intersect this interval with a target interval, optionally return their intersection. */
  def project(target: RawInterval[T]): Option[RawInterval[T]] = {
    if (start >= target.end || end <= target.start) { // no overlap
      None
    } else if (start >= target.start && end < target.end) { // totally within target
      Some(this)
    } else if (start < target.start && end > target.start && end < target.end) { // starts before target, ends within
      Some(RawInterval(target.start, end))
    } else if (start < target.start && end >= target.end) { // starts before target ends after
      Some(target)
    } else if (start >= target.start && start < target.end && end >= target.end) { // starts within, ends after
      Some(RawInterval(start, target.end))
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

/** Support for RawIntervals including a factory for creating an Interval from an event and a method
 *  for coalescing a collection of RawIntervals. */
object RawInterval {

  /** Create a RawInterval from an Event. The event key is interpreted as the start and the event
   *  value is interpreted as a length. Supports long or double keys and long or double lenghts. */
  def apply[T: Numeric, U: Numeric](event: Event[T, U]): RawInterval[T] = {
    val numericKey = implicitly[Numeric[T]]
    val numericValue = implicitly[Numeric[U]]

    def start: T = event.argument

    val end: T = start match {
      case startDouble: Double => numericKey.fromDouble(startDouble + event.value.toDouble)
      case startFloat: Float   => numericKey.fromDouble(startFloat + event.value.toDouble)
      case integer             => numericKey.fromLong(integer.toLong + event.value.toLong)
    }

    RawInterval(start, end)
  }

  /** Combine a collection of intervals, returning a non-overlapping set of intervals that
   *  match the coverage.  Assumes that the intervals are presented in sorted order. */
  def combine[T:Numeric](intervals: Seq[RawInterval[T]]): Seq[RawInterval[T]] = {
    
    /** return true if two intervals overlap. the two intervals must be in start-sorted order */
    def overlapNext[T:Numeric](current:RawInterval[T], next:RawInterval[T]):Boolean = {
      next.start < current.end
    }
    
    /** combine two intervals that are known to overlap each other. The two intervals must be in start sorted order */
    def combineOverlapped[T:Numeric](current:RawInterval[T], next:RawInterval[T]):RawInterval[T] = {
      RawInterval(current.start, spire.math.max(current.end, next.end))
    }

    /** State for incremental processing of combined intervals.
     *  
      * @current active interval, might still be combined with subsequent intervals
      * @emit complete interval ready to be output, won't be combined anymore
      */
    case class State(current: Option[RawInterval[T]], emit: Option[RawInterval[T]])

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

  /** Return the sum of the lengths of the intervals, does not check for overlap */
  def sumIntervals[T: Numeric, U: Numeric](intervals: Seq[RawInterval[T]]): U = {
    val numericKey = implicitly[Numeric[T]]
    val numericValue = implicitly[Numeric[U]]
    val overlaps = intervals.map(_.length)
    overlaps match {
      case Seq() =>
        numericValue.zero
      case nonEmpty =>
        val overlapT = nonEmpty.reduce(_ + _)
        numericKey.toType[U](overlapT)
    }
  }


  /** Return the intersection of a set of intervals with a joda Interval,
   *  interpreting the intervals as containg epoch milliseconds */
  def jodaMillisIntersections[T: Numeric]( // format: OFF
      intervals: Seq[RawInterval[T]],
      jodaInterval: JodaInterval): Seq[RawInterval[T]] = { // format: ON
    val start: T = Numeric[Long].toType[T](jodaInterval.start.millis)
    val end: T = Numeric[Long].toType[T](jodaInterval.end.millis)
    val period = RawInterval[T](start, end)
    intervals.flatMap { interval => interval.project(period) }
  }


  
  
}
