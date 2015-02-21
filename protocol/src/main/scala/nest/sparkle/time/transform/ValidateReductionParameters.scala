package nest.sparkle.time.transform

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success, Try}
import scala.util.control.Exception.nonFatalCatch

import spray.json.{JsObject, JsonFormat}

import nest.sparkle.datastream._
import nest.sparkle.time.protocol.SummaryParameters
import nest.sparkle.time.protocol.TransformParametersJson.SummaryParametersFormat
import nest.sparkle.time.transform.TransformValidation.{columnsKeyType, rangeExtender, summaryPeriod}
import nest.sparkle.util._
import nest.sparkle.util.TryToFuture.FutureTry

case class ReductionParameterError(msg: String) extends RuntimeException(msg)

/** validate request parameters for reduction transforms */ 
// note: case class (rather than a singleton object) because we'll probably want to pass a Config here
private[transform] case class ValidateReductionParameters() {
  
  /** verify that the request parameters and type of columns in the store are what we expect */
  def validate[K]( // format: OFF
        futureGroups:Future[Seq[ColumnGroup]], 
        transformParameters: JsObject
      ) (implicit execution: ExecutionContext)
      : Future[ValidReductionParameters[K]] = { // format: ON

    for {
      // validate request parameters and match to existing types
      columnGroups <- futureGroups
      (keyType, keyJson, keyOrdering) <- recoverKeyTypes[K](columnGroups).toFuture
      reductionParameters <- parseParameters(transformParameters)(keyJson).toFuture
      ongoingDuration <- parseOngoingDuration(reductionParameters.ongoingBufferPeriod).toFuture
      grouping <- validateGrouping(reductionParameters).toFuture
    } yield {
      ValidReductionParameters(keyType, keyJson, keyOrdering, reductionParameters, grouping,
                               ongoingDuration)
    }
  }

  /** validate the grouping parameters in the client request.
    * Only one of byElementCount, intoDurationParts, etc. may be specified in a legal client
    * message, and any provided duration strings must be parseable. */
  private def validateGrouping[K](reductionParameters:SummaryParameters[K])
     : Try[Option[GroupingType]] = {
    val allVariants =
      reductionParameters.intoCountedParts ++
        reductionParameters.intoDurationParts ++
        reductionParameters.partByCount ++
        reductionParameters.partBySize

    /** return the first GroupingType we find that parses successfully */
    def firstGrouping:Try[Option[GroupingType]] = {
      import reductionParameters._
      val basicOptions:Option[GroupingType] =
        intoCountedParts.map {
          IntoCountedParts(_)
        } orElse {
          intoDurationParts.map(IntoDurationParts(_))
        } orElse {
          partByCount.map(ByCount(_))
        }

      lazy val periodOption: Try[Option[ByDuration]] = {
        val periodTry = summaryPeriod(partBySize, timeZoneId)
        periodTry map { optPeriod =>
          optPeriod.map { period =>
            ByDuration(period)
          }
        }
      }

      basicOptions match {
        case Some(grouping) => Success(Some(grouping))
        case _              => periodOption
      }

    }

    allVariants.size match {
      case 0|1 => firstGrouping
      case n   => Failure(ReductionParameterError("max one grouping option, not $n. $reductionParameters"))
    }
  }

  /** Return the JsonFormat and Ordering Typeclasses for the key columns  */
  private def recoverKeyTypes[T](columnGroups: Seq[ColumnGroup]) // Format: OFF
  : Try[(TypeTag[T], JsonFormat[T], Ordering[T])] = { // Format: ON
    for {
      keyType <- columnsKeyType[T](columnGroups)
      jsonKey <- RecoverJsonFormat.tryJsonFormat[T](keyType)
      orderingKey <- RecoverOrdering.tryOrdering[T](keyType)
    } yield {
      (keyType, jsonKey, orderingKey)
    }
  }

  /** Try to convert protocol request transformParameters field into IntervalParameters */
  private def parseParameters[T: JsonFormat](transformParameters: JsObject) // format: OFF
      : Try[SummaryParameters[T]] = { // format: ON
    nonFatalCatch.withTry { transformParameters.convertTo[SummaryParameters[T]] }
  }

  /** parse the ongoingDuration field from the transformParameters */
  private def parseOngoingDuration(optPeriod:Option[String]): Try[Option[FiniteDuration]] = {
    optPeriod match {
      case Some(periodString) =>
        Period.parse(periodString) match {
          case Some(period) =>
            val duration =FiniteDuration(period.utcMillis, TimeUnit.MILLISECONDS)
            Success(Some(duration))
          case None =>
            Failure(new PeriodParseException(periodString))
        }
      case None =>
        Success(None)
    }
  }
  
}

/** container for successfully validated reduction parameters */
private[transform] case class ValidReductionParameters[K](
  keyType: TypeTag[K],
  keyJsonFormat: JsonFormat[K],
  ordering: Ordering[K],
  reductionParameters: SummaryParameters[K],
  grouping: Option[GroupingType],
  ongoingDuration: Option[FiniteDuration])

