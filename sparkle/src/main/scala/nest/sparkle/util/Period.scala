package nest.sparkle.util

import org.joda.time.DurationFieldType
import scala.util.control.Exception._

/** a period of time*/
case class Period(value: Double, durationType: DurationFieldType)

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
      baseValue <- nonFatalCatch opt java.lang.Double.parseDouble(number)
      if (baseValue >= 0)
      unit <- units.find(_.name == unitsString)
      value = baseValue * unit.scaleFactor
    } yield {
      Period(value, unit.durationType)
    }
  }

  /** parse a string like "1 hour" into a Period */
  def unapply(duration: String): Option[Period] = parse(duration)

}