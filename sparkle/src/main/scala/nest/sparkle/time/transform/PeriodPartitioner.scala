package nest.sparkle.time.transform

import nest.sparkle.util.Period
import java.util.TimeZone
import org.joda.time.DateTime
import com.github.nscala_time.time.Implicits._
import nest.sparkle.store.Event
import nest.sparkle.time.protocol.RangeInterval
import spire.math.Numeric
import spire.implicits._
import org.joda.time.DateTimeZone
import nest.sparkle.util.PeriodWithZone
import org.joda.time.{Interval => JodaInterval}

/** Support for dividing time segments into periods, e.g. 1984 into months */
object PeriodPartitioner {
  /** return an iterator that iterates over period sized portions of a joda interval  */
  def timePartitions(period: Period, fullInterval: JodaInterval): Iterator[JodaInterval] = {
    val partPeriod = period.toJoda

    def nonEmptyParts(): Iterator[JodaInterval] = {
      var partStart = period.roundDate(fullInterval.start)
      var partEnd = fullInterval.start

      new Iterator[JodaInterval] {
        override def hasNext: Boolean = partEnd < fullInterval.end
        override def next(): JodaInterval = {
          partEnd = partStart + partPeriod
          val interval = new JodaInterval(partStart, partEnd)
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
    val startOpt = range.start.orElse(events.headOption.map(_.argument))
    val (endOpt, includeEnd) = range.until match {
      case explicitEnd: Some[T] => (explicitEnd, false)
      case None                 => (endOfLatestInterval(events), true)
    }

    // iterator that walks period by period (defined if the period ends are available)
    val iteratorByPeriod:Option[() => Iterator[JodaInterval]] =
      for {
        start <- startOpt
        end <- endOpt
      } yield {
        val endDate = new DateTime(end)
        val startDate = {
          val baseStartDate = new DateTime(start, dateTimeZone)
          period.roundDate(baseStartDate)
        }
        val fullRange = new JodaInterval(startDate, endDate)
        () => timePartitions(period, fullRange)
      }

    val partIterator = iteratorByPeriod.getOrElse { () => Iterator.empty }

    TimePartitions(partIterator, includeEnd, dateTimeZone)
  }

  /** Optionally returns the end of the last item, where the item is interpreted as
   *  a start, duration pair.  */
  private def endOfLatestInterval[T: Numeric, U: Numeric](events: Seq[Event[T, U]]): Option[T] = {
    events.headOption.map { _ =>
      val numericKey = implicitly[Numeric[T]]
      val ends = events.map { case Event(key, value) => key.toLong + value.toLong }
      numericKey.fromLong(ends.max)
    }
  }
}

/** Enables iterating over partitions of time */
case class TimePartitions(partIterator: () => Iterator[JodaInterval], includeEnd: Boolean, dateTimeZone: DateTimeZone)
