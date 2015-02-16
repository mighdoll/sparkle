package nest.sparkle.time.transform

import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.reflect.runtime.universe._
import nest.sparkle.util.KindCast.castKind
import org.joda.time.DateTimeZone
import java.util.TimeZone
import nest.sparkle.util.{ Period, PeriodWithZone, RecoverJsonFormat, RecoverOrdering }
import nest.sparkle.util.OptionConversion._
import spire.math.Numeric
import spire.implicits._
import scala.concurrent.duration._

/** handy validation functions for protocol transform implementations to validate request formats
 *  and database format */
private[transform] object TransformValidation {

  /** Return the key type of the columns.
    *
    * If no columns are specified, or the key types of the coloumns don't match, return an error.
    */
  def columnsKeyType[T](columnGroups: Seq[ColumnGroup]) // format: OFF
      : Try[TypeTag[T]] = { // format: ON

    val keyTypes = for { group <- columnGroups; column <- group.columns } yield column.keyType
    keyTypes.length match {
      case 0                                      => Failure(NoColumnSpecified)
      case _ if keyTypes.forall(_ == keyTypes(0)) => Success(castKind(keyTypes(0)))
      case _                                      => Failure(InconsistentColumnsSpecified())
    }
  }
  
  
  /** parse the period string (e.g. "1 year") from a protocol request */
  def summaryPeriod(optPeriodString: Option[String], optZone: Option[String]): Try[Option[PeriodWithZone]] = {
    optPeriodString match {
      case Some(periodString) =>
        periodParameter(periodString, optZone)
      case None =>
        Success(None) // we parsed successfully: there was no period
    }
  }
  
  def rangeExtender[T: Numeric]: ExtendRange[T] = {
    val numericKey = implicitly[Numeric[T]]
    ExtendRange[T](before = Some(numericKey.fromLong(-1.day.toMillis))) // TODO make adjustable in the .conf file
  }
  
  /** Parse the client-protocol supplied period (e.g. "1 month") */
  private def periodParameter(periodString: String, optZone: Option[String]): Try[Some[PeriodWithZone]] = {
    for {
      period <- Period.parse(periodString).toTryOr(InvalidPeriod(periodString))
    } yield {
      Some(PeriodWithZone(period, requestedTimeZone(optZone)))
    }
  }

  /** the default timezone to use for date centric calclucation if none is provided by the caller's request */
  private val defaultDateTimeZone = DateTimeZone.UTC

  /** The timezone from the request, or the default timezone if not is specified */
  private def requestedTimeZone(optZone: Option[String]): DateTimeZone = {
    optZone.map { zoneString =>
      val timeZone = TimeZone.getTimeZone(zoneString)
      DateTimeZone.forTimeZone(timeZone)
    } getOrElse defaultDateTimeZone
  }


}