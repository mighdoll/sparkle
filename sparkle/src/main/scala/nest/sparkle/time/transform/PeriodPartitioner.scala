package nest.sparkle.time.transform

import nest.sparkle.util.Period
import java.util.TimeZone
import org.joda.time.DateTime
import org.joda.time.Interval
import com.github.nscala_time.time.Implicits._
import nest.sparkle.store.Event
import nest.sparkle.time.protocol.RangeInterval
import spire.math.Numeric
import spire.implicits._
import org.joda.time.DateTimeZone
import nest.sparkle.util.PeriodWithZone

object PeriodPartitioner {
  def timePartitions(period: Period, fullInterval: Interval): Iterator[Interval] = {
    val partPeriod = period.toJoda

    def nonEmptyParts(): Iterator[Interval] = {
      var partStart = period.roundDate(fullInterval.start)
      var partEnd = fullInterval.start

      new Iterator[Interval] {
        override def hasNext(): Boolean = partEnd < fullInterval.end
        override def next(): Interval = {
          partEnd = partStart + partPeriod
          val interval = new Interval(partStart, partEnd)
          partStart = partEnd
          interval
        }
      }
    }
    if (partPeriod.getValues.forall(_ == 0)) {
      Iterator.empty
    } else {
      nonEmptyParts()
    }
  }

  /** events keys are interpreted as epoch milliseconds
    * event values are interpeted as millisceond durations
    */
  def timePartitionsFromRequest[T: Numeric, U: Numeric]( // format: OFF
      events: Seq[Event[T, U]],
      rangeOpt: Option[RangeInterval[T]],
      periodWithZone: PeriodWithZone): TimePartitions = { // format: ON
    // TODO DRY me with SummaryTransform!
    // TODO make configurable based on storage time type..

    val PeriodWithZone(period, dateTimeZone) = periodWithZone
    val range = rangeOpt.getOrElse(RangeInterval())
    val start = range.start.getOrElse(events.head.argument)
    val (end, includeEnd) = range.until match {
      case Some(explicitEnd) => (explicitEnd, false)
      case None              => (endOfLatestInterval(events), true)
    }

    val endDate = new DateTime(end)
    val startDate = {
      val baseStartDate = new DateTime(start, dateTimeZone)
      period.roundDate(baseStartDate)
    }
    val fullRange = new Interval(startDate, endDate)
    def partIterator() = timePartitions(period, fullRange)

    TimePartitions(partIterator, includeEnd, dateTimeZone)
  }

  private def endOfLatestInterval[T: Numeric, U: Numeric](events: Seq[Event[T, U]]): T = {
    val numericKey = implicitly[Numeric[T]]
    val ends = events.map { case Event(key, value) => key.toLong + value.toLong }
    numericKey.fromLong(ends.max)
  }

}

case class TimePartitions(partIterator: () => Iterator[Interval], includeEnd: Boolean, dateTimeZone: DateTimeZone)
