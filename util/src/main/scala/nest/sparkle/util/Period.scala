package nest.sparkle.util

import scala.util.control.Exception._

import com.github.nscala_time.time.Implicits._
import org.joda.time.{DateTime, DateTimeZone, DurationFieldType}
import org.joda.time.{Period => JodaPeriod}

case class PeriodWithZone(period:Period, dateTimeZone: DateTimeZone)

case class PeriodParseException(message:String) extends RuntimeException(message)

/** A period that supports rounding date times to the same resolution, and converting to JodaPeriod */
case class Period(value: Int, durationType: DurationFieldType) {

  def toJoda: JodaPeriod = {
    import nest.sparkle.util.FieldTypes._
    durationType match {
      case `millis`  => JodaPeriod.millis(value)
      case `seconds` => JodaPeriod.seconds(value)
      case `minutes` => JodaPeriod.minutes(value)
      case `hours`   => JodaPeriod.hours(value)
      case `days`    => JodaPeriod.days(value)
      case `weeks`   => JodaPeriod.weeks(value)
      case `months`  => JodaPeriod.months(value)
      case `years`   => JodaPeriod.years(value)
    }
  }

  /** Return a DateTime at or before date that starts on an even boundary of time per this period,
    * e.g. round to the nearest month if the period is 1 month, round to the nearest 15 minutes if
    * the period is 15 minutes, etc. Only works if this period is less than the next duration type
    * (e.g. only works for minutes if the value is less than 60, only works for hours if the value
    * is less than 24), otherwise, we only round to the the same resolution as this period.
    */
  def roundDate(date: DateTime): DateTime = {
    import nest.sparkle.util.FieldTypes._
    durationType match {
      case `millis`  => date.withMillisOfSecond(roundedValue(date.getMillisOfSecond))
      case `seconds` => date.withMillisOfSecond(0).withSecond(roundedValue(date.getSecondOfMinute))
      case `minutes` => date.withMillisOfSecond(0).withSecond(0).withMinute(roundedValue(date.getMinuteOfHour))
      case `hours`   => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(roundedValue(date.getHourOfDay))
      case `days`    => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(0)
      case `weeks`   => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(0)
      case `months`  => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(0).withDay(1).withMonth(roundedValue(date.getMonthOfYear))
      case `years`   => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(0).withDay(1).withMonth(1)
      case _         => ???
    }
  }

  private def roundedValue(input : Int): Int = {
    (input / value) * value
  }

  private def epochZero = new DateTime(0L)
  /** length of this period w/o timezone considerations */
  def utcMillis:Long = {
    val end = epochZero + toJoda
    end.getMillis
  }
}

/** utility for parsing time period strings */
object Period {
  private case class PeriodUnit(name: String, durationType: DurationFieldType, scaleFactor: Double = 1.0)

  private val baseUnits = Seq(
    PeriodUnit("microsecond", DurationFieldType.millis, 1 / 1000.0),
    PeriodUnit("millisecond", DurationFieldType.millis),
    PeriodUnit("second", DurationFieldType.seconds),
    PeriodUnit("minute", DurationFieldType.minutes),
    PeriodUnit("hour", DurationFieldType.hours),
    PeriodUnit("day", DurationFieldType.days),
    PeriodUnit("week", DurationFieldType.weeks),
    PeriodUnit("month", DurationFieldType.months),
    PeriodUnit("year", DurationFieldType.years)
  )

  private val plurals = baseUnits.map{ unit => unit.copy(name = unit.name + "s") }
  private val units = baseUnits ++ plurals

  /** parse a string like "1 hour" into a Period */
  def parse(duration: String): Option[Period] = {
    val Array(number, unitsString) = duration.split(" ").filterNot(_.isEmpty)

    for {
      baseValue <- nonFatalCatch opt Integer.parseInt(number)
      if (baseValue >= 0)
      unit <- units.find(_.name == unitsString)
      value = (baseValue * unit.scaleFactor).toInt
    } yield {
      Period(value, unit.durationType)
    }
  }

  /** parse a string like "1 hour" into a Period */
  def unapply(duration: String): Option[Period] = parse(duration)
}

/** convenience names for joda field types, so that we can pattern match with them */
private object FieldTypes {
  val millis = DurationFieldType.millis
  val seconds = DurationFieldType.seconds
  val minutes = DurationFieldType.minutes
  val hours = DurationFieldType.hours
  val days = DurationFieldType.days
  val weeks = DurationFieldType.weeks
  val months = DurationFieldType.months
  val years = DurationFieldType.years
}


