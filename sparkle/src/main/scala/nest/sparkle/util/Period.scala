package nest.sparkle.util

import org.joda.time.DurationFieldType
import scala.util.control.Exception._
import org.joda.time.{ Period => JodaPeriod }
import com.github.nscala_time.time.Implicits._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone


case class PeriodWithZone(period:Period, dateTimeZone:DateTimeZone)

/** A period that supports rounding date times to the same resolution, and converting to JodaPeriod */
case class Period(value: Int, durationType: DurationFieldType) {

  def toJoda: JodaPeriod = {
    import FieldTypes._
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

  /** return a DateTime at or before date that starts on an even boundary of time with the same resolution as this period
    * e.g. round to the nearest month if the period is months.
    */
  def roundDate(date: DateTime): DateTime = { // TODO should round to the nearest modulus of even values too (e.g. 3hr boundaries)
    import FieldTypes._
    durationType match {
      case `millis`  => date
      case `seconds` => date.withMillisOfSecond(0)
      case `minutes` => date.withMillisOfSecond(0).withSecond(0)
      case `hours`   => date.withMillisOfSecond(0).withSecond(0).withMinute(0)
      case `days`    => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(0)
      case `weeks`   => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(0)
      case `months`  => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(0).withDay(1)
      case `years`   => date.withMillisOfSecond(0).withSecond(0).withMinute(0).withHour(0).withDay(1).withMonth(1)
      case _         => ???
    }
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


