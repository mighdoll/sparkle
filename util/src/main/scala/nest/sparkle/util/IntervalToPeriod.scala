package nest.sparkle.util

import scala.collection.SortedSet

import org.joda.time.DurationFieldType

import nest.sparkle.measure.Milliseconds

/** utility for converting to millisecond intervals to human friendly rounded periods.
  * e.g. when creating a bar chart that has room for 5 bars, this routine will round
  * a target time duration for each bar to a reasonable size. See the code for details
  * of what's reasonable. For example, hours and minutes are considered reasonably
  * divisible only into 1,15,30 or 60 minute sizes. */
object IntervalToPeriod {
  private val second = 1000L
  private val minute = second * 60
  private val hour = minute * 60
  private val day = hour * 24
  private val week = day * 7
  private val year = day * 365

  /** return the nearest 'round' time period smaller than this millisecond interval.
    * e.g. '5 minutes' or '30 seconds'.  */
  def millisToRoundedPeriod(millis: Milliseconds): Period = {
    millis.value match {
      case v if v <= second =>
        Period(roundValue(v, SortedSet(1,5,10,25,50,100,250,500)), DurationFieldType.millis)
      case v if v <= minute =>
        Period(roundValue(v/second, SortedSet(1,15,30,60)), DurationFieldType.seconds)
      case v if v <= hour =>
        Period(roundValue(v/minute, SortedSet(1,15,30,60)), DurationFieldType.minutes)
      case v if v <= day =>
        Period(roundValue(v/hour, SortedSet(1,2,6,12,24)), DurationFieldType.hours)
      case v if v <= week =>
        Period(roundValue(v/day, SortedSet(1)), DurationFieldType.days)
      case v if v < year =>
        Period(roundValue(v/week, SortedSet(1)), DurationFieldType.weeks)
      case v =>
        Period(roundValue(v/year, SortedSet(1)), DurationFieldType.years)
    }
  }

  /** return the largest roundValue larger than value, or the smallest power of 10 smaller
    * than roundValue */
  private[util] def roundValue(value:Long, roundValues:SortedSet[Int]): Int = {
    roundValues.toSeq.sliding(2).collectFirst {
      case Seq(a,b) if value > a && value <= b   =>
        b
    }.getOrElse {
      if (value < roundValues.head) {
        roundValues.head
      } else if (value <= roundValues.last) {
        roundValues.last
      } else {
        val power = math.floor(math.log10(value)).toInt
        math.pow(10, power).toInt
      }
    }
  }
}
