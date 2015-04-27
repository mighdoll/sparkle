package nest.sparkle.store

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._
import rx.lang.scala.Observable
import spire._

import nest.sparkle.core.OngoingData
import nest.sparkle.util.RecoverNumeric
import nest.sparkle.util.RecoverOrdering
import nest.sparkle.measure.Span

/** Convert a set of on/off columns into a column of intervals
  * IntervalColumns are 'virtual' key-value columns, where the key is interpreted as time and the value is
  * intepreted as duration. Interval durations are virtual in the sense that duration is not read directly
  * from storage. Instead durations is calculated on the fly from underlying storage columns containing
  * boolean on/off values.
  *
  * Additionally, multiple Interval columns may be merged into a single one. Overlapping intervals
  * are combined.
  *
  * If the last event in the underlying data in an 'on' event, the last interval generated
  * will have a length of zero.
  *
  * @param earlyRead offset explicit readRange start intervals by this amount. 'On' events may have
  * started prior to the target readRange, identifying these intervals that are ongoing at the readRange
  * start requires reading some earlier data to find the On.
  *
  * LATER consider reading in reverse order in the column to find the previous entry, rather than
  * starting a fixed amount of time earlier. This will be somewhat slower
  */
case class IntervalColumn[T](sourceColumns: Seq[Column[T, Boolean]], earlyRead: Option[T] = None)
    extends Column[T, T] {
  override def name = "Interval"
  override def keyType = sourceColumns.head.keyType
  override def valueType = keyType

  import spire.implicits._
  implicit val numeric = RecoverNumeric.optNumeric[T](keyType).get // TODO how to pass error back up?
  implicit val keyOrdering = RecoverOrdering.ordering[T](keyType)

  override def readRangeOld // format: OFF
      (start: Option[T] = None, end: Option[T] = None, limit: Option[Long] = None, parentSpan:Option[Span])
      (implicit execution: ExecutionContext): OngoingEvents[T,T] = { // format: ON

    // TODO large data gaps (e.g. >24 hr) should be assumed to be off, probably.. (configurable)
    val modifiedStart =
      start.map { specifiedStart =>
        val earlyAdjust = earlyRead.getOrElse(numeric.zero)
        specifiedStart - earlyAdjust
      }

    val reads = sourceColumns.map { column => column.readRangeOld(modifiedStart, end, limit) }
    val allIntervals = reads.map { ongoingEvents => toInterval(ongoingEvents.initial) }
    val initial = compositeIntervals(allIntervals) // TODO this should be a separate transform
    val ongoing: Observable[Event[T, T]] = Observable.empty // LATER handle ongoing events too, for now we only handle the initial set
    OngoingEvents(initial, ongoing)
  }

  override def readRange
      ( start: Option[T] = None,
        end: Option[T] = None,
        limit: Option[Long] = None,
        parentSpan: Option[Span] )
      ( implicit execution: ExecutionContext): OngoingData[T, T] = {
    ???
  }

  /** convert on/off events to intervals */
  private def toInterval(onOffs: Observable[Event[T, Boolean]]): Observable[Event[T, T]] = {
    // As we walk through the on/off data, produce this state record. 
    // We save the intermediate state so that we can:
    // 1) accumulate the length of 'on' periods, even across sequences like: off,on,on,on,off
    // 2) emit a final record in case the final on period hasn't yet closed
    // 3) LATER - optionally add 'off' signals in case of data gaps
    case class IntervalState(current: Option[Event[T, T]], emit: Option[Event[T, T]]) {
      require(current.isEmpty || emit.isEmpty)
    }

    val intervalStates =
      onOffs.scan(IntervalState(None, None)){ (state, event) =>
        (state.current, event.value) match {
          case (Some(Event(start, _)), true) => // accumulate into current interval, making it a bit longer
            val size = event.key - start
            IntervalState(Some(Event(start, size)), None)
          case (Some(Event(start, _)), false) => // end started interval 
            val size = event.key - start
            val emit = Event(start, size)
            IntervalState(None, Some(emit))
          case (None, true) => // start a new zero length interval
            IntervalState(Some(Event(event.key, numeric.zero)), None)
          case (None, false) => // continue no interval
            state
        }
      }

    val mainIntervals = intervalStates.flatMap { state =>
      state match { // emit the completed intervals as events
        case IntervalState(_, Some(emit)) => Observable.from(List(emit))
        case _                            => Observable.empty
      }
    }

    val lastInterval = intervalStates.last.flatMap { state =>
      state match {
        // close and emit a final started-but-not-yet-completed event. 
        case IntervalState(Some(event), _) => Observable.from(List(event))
        case _                             => Observable.empty
      }
    }

    val intervals = mainIntervals ++ lastInterval
    intervals
  }

  /** combine a set of intervals in multiple streams into one stream, merging whenever their ranges intersect */
  private def compositeIntervals(intervals: Seq[Observable[Event[T, T]]]) // format: OFF
      : Observable[Event[T,T]] = { // format: ON

    // TODO DRY this copypasta with RawInterval!
    case class State(current: Option[Event[T, T]], emit: Option[Event[T, T]])
    def combineEvents(events: Seq[Event[T, T]]): Seq[Event[T, T]] = {
      val states = events.scanLeft(State(None, None)) { (state, event) =>
        (state, event) match {
          case (State(None, _), event) => // start event, but don't emit yet
            State(Some(event), None)
          case (State(Some(current), _), event) if overlap(current, event) => // combine, but don't emit yet
            State(Some(combine(current, event)), None)
          case (State(Some(current), _), event) => // !overlap emit previous, start new one
            State(Some(event), Some(current))
        }
      }
      val initial = states.flatMap(_.emit)
      val last = states.lastOption.flatMap(_.current)
      initial ++ last
    }

    def overlap(first: Event[T, T], second: Event[T, T]): Boolean = {
      val firstEnd = first.key + first.value
      second.key < firstEnd
    }

    def combine(first: Event[T, T], second: Event[T, T]): Event[T, T] = {
      val firstOverlap = second.key - first.key
      val length = firstOverlap + second.value
      Event(first.key, length)
    }

    val joined = intervals.reduce{ (a, b) => a.merge(b) }

    val combined =
      joined.toSeq.flatMap { all =>
        // note this awaits all the streams to complete. To do this on async arriving data,
        // perhaps buffer and assume that all source events that overlap are available in the
        // buffered window..
        val sorted = all.sortBy(_.key)
        Observable.from(combineEvents(sorted))
      }

    combined

  }

}