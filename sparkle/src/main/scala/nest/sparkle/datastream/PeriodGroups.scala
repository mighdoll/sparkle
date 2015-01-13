package nest.sparkle.datastream

import nest.sparkle.util.PeriodWithZone
import org.joda.time.DateTime
import nest.sparkle.util.Period
import org.joda.time.{Interval => JodaInterval}
import spire.math.Numeric
import spire.implicits._
import com.github.nscala_time.time.Implicits._

object PeriodGroups {
  
  def jodaIntervals[K: Numeric](periodWithZone: PeriodWithZone, startKey: K): Iterator[JodaInterval] = {
    val PeriodWithZone(period, dateTimeZone) = periodWithZone
    val startDate: DateTime = {
      val baseStartDate = new DateTime(startKey, dateTimeZone)
      period.roundDate(baseStartDate)
    }
    timePartitions(period, startDate)
  }

  /** return an iterator that iterates over period sized portions */
  private def timePartitions(period: Period, startDate: DateTime): Iterator[JodaInterval] = {
    val partPeriod = period.toJoda

    def nonEmptyParts(): Iterator[JodaInterval] = {
      var partStart = period.roundDate(startDate)
      var partEnd: DateTime = null

      new Iterator[JodaInterval] {
        override def hasNext: Boolean = true
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
}