package nest.sparkle.time.transform

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{FiniteDuration, Duration}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success, Try}
import scala.util.control.Exception.nonFatalCatch

import spray.json.{JsObject, JsonFormat}

import nest.sparkle.time.protocol.SummaryParameters
import nest.sparkle.time.protocol.TransformParametersJson.SummaryParametersFormat
import nest.sparkle.time.transform.TransformValidation.{columnsKeyType, rangeExtender, summaryPeriod}
import nest.sparkle.util._
import nest.sparkle.util.TryToFuture.FutureTry

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
    // TODO handle partByCount case here too
      periodSize <- summaryPeriod(reductionParameters.partBySize,
                                  reductionParameters.timeZoneId).toFuture
      ongoingDuration <- parseOngoingDuration(reductionParameters.ongoingBufferPeriod).toFuture
    } yield {
      ValidReductionParameters(keyType, keyJson, keyOrdering, reductionParameters, periodSize,
                               ongoingDuration)
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
  periodSize: Option[PeriodWithZone],
  ongoingDuration: Option[FiniteDuration])

