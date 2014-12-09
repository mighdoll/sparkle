package nest.sparkle.time.transform

import nest.sparkle.time.protocol.IntervalParameters
import spray.json.JsObject
import nest.sparkle.util.PeriodWithZone
import scala.reflect.runtime.universe._
import scala.concurrent.ExecutionContext
import spray.json.JsonFormat
import scala.concurrent.Future
import scala.util.control.Exception.nonFatalCatch
import scala.util.{ Failure, Success, Try }
import nest.sparkle.util.KindCast.castKind
import nest.sparkle.util.TryToFuture.FutureTry
import nest.sparkle.util.{ Period, PeriodWithZone, RecoverJsonFormat, RecoverNumeric, RecoverOrdering }
import spray.json.DefaultJsonProtocol._
import spire.math.Numeric
import nest.sparkle.time.protocol.TransformParametersJson.IntervalParametersFormat
import nest.sparkle.util.OptionConversion.OptionFuture
import org.joda.time.DateTimeZone
import java.util.TimeZone
import com.typesafe.config.Config

/** encodes some request validation results
  * . typeclasses for the column keys (from the stored key type in the db for the requested columns)
  * . transform parameter parsing
  * . period parameter parsing
  */
protected case class ValidParameters[K](
  keyType: TypeTag[K],
  numeric: Numeric[K],
  jsonFormat: JsonFormat[K],
  ordering: Ordering[K],
  intervalParameters: IntervalParameters[K],
  periodSize: Option[PeriodWithZone])

/** The transform required boolean values, but the specified column doesn't have booleans in it */
case class NonBooleanValues() extends RuntimeException()

/** validate protocol request parameters for an onOffIntervalSum transform */
case class OnOffParameters(rootConfig: Config) {

  /** verify that the request parameters and type of columns in the store are what we expect */
  def validate[K]( // format: OFF
        futureGroups:Future[Seq[ColumnGroup]], 
        transformParameters: JsObject
      ) (implicit execution: ExecutionContext)
      : Future[ValidParameters[K]] = { // format: ON

    for {
      // validate request parameters and match to existing types
      columnGroups <- futureGroups
      (keyType, keyNumeric, keyJson, keyOrdering) <- recoverKeyTypes[K](columnGroups).toFuture
      intervalParameters <- parseParameters(transformParameters)(keyJson).toFuture
      periodSize <- summaryPeriod(intervalParameters.partBySize, intervalParameters.timeZoneId).toFuture
      _ <- booleanValues(columnGroups).toFuture
    } yield {
      ValidParameters(keyType, keyNumeric, keyJson, keyOrdering, intervalParameters, periodSize)
    }
  }

  /** Try to convert protocol request transformParameters field into IntervalParameters */
  private def parseParameters[T: JsonFormat](transformParameters: JsObject): Try[IntervalParameters[T]] = {
    nonFatalCatch.withTry { transformParameters.convertTo[IntervalParameters[T]] }
  }

  /** Return the JsonFormat and Numeric Typeclasses for the key columns  */
  private def recoverKeyTypes[T](columnGroups: Seq[ColumnGroup]) // format: OFF
      : Try[(TypeTag[T], Numeric[T], JsonFormat[T], Ordering[T])] = { // format: ON
    for {
      keyType <- columnsKeyType[T](columnGroups)
      jsonKey <- RecoverJsonFormat.tryJsonFormat[T](keyType)
      numericKey <- RecoverNumeric.tryNumeric[T](keyType)
      orderingKey <- RecoverOrdering.tryOrdering[T](keyType)
    } yield {
      (keyType, numericKey, jsonKey, orderingKey)
    }
  }

  /** Return the key type of the columns.
    *
    * If no columns are specified, or the key types of the coloumns don't match, return an error.
    */
  private def columnsKeyType[T](columnGroups: Seq[ColumnGroup]) // format: OFF
      : Try[TypeTag[T]] = { // format: ON

    val keyTypes = for { group <- columnGroups; column <- group.columns } yield column.keyType
    keyTypes.length match {
      case 0                                      => Failure(NoColumnSpecified)
      case _ if keyTypes.forall(_ == keyTypes(0)) => Success(castKind(keyTypes(0)))
      case _                                      => Failure(InconsistentColumnsSpecified())
    }
  }

  /** verify that the collection of columns contains only boolean values (no values of other types) */
  private def booleanValues(columnGroups: Seq[ColumnGroup]): Try[Unit] = {
    val valueTypes =
      for {
        group <- columnGroups
        itemColumn <- group.columns
      } yield itemColumn.valueType

    if (valueTypes.forall(_ == typeTag[Boolean])) {
      Success(())
    } else {
      Failure(NonBooleanValues())
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

  /** Parse the client-protocol supplied period (e.g. "1 month")
    */ // TODO move to somewhere generic
  private def periodParameter(periodString: String, optZone: Option[String]): Try[Some[PeriodWithZone]] = {
    for {
      period <- Period.parse(periodString).toTryOr(InvalidPeriod(periodString))
    } yield {
      Some(PeriodWithZone(period, requestedTimeZone(optZone)))
    }
  }

  /** the default timezone to use for date centric calclucation if none is provided by the caller's request */
  private val defaultDateTimeZone = {
    val timeZone = TimeZone.getTimeZone("America/Los_Angeles") // TODO make this .conf settable, default UTC
    DateTimeZone.forTimeZone(timeZone)
  }

  /** The timezone from the request, or the default timezone if not is specified */
  private def requestedTimeZone(optZone: Option[String]): DateTimeZone = {
    optZone.map { zoneString =>
      val timeZone = TimeZone.getTimeZone(zoneString)
      DateTimeZone.forTimeZone(timeZone)
    } getOrElse defaultDateTimeZone
  }

}