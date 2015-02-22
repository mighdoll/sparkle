package nest.sparkle.datastream

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success}

import rx.lang.scala.Observable

import nest.sparkle.measure.Span
import nest.sparkle.util.{Log, PeriodWithZone, RecoverNumeric}


/** how to group items for a redution operation */
case class ReductionGrouping(
  maxParts: Int,
  grouping: Option[GroupingType]
)

sealed trait GroupingType
case class ByDuration(duration:PeriodWithZone) extends GroupingType
case class ByCount(count:Int) extends GroupingType
case class IntoCountedParts(count:Int) extends GroupingType
case class IntoDurationParts(count:Int) extends GroupingType



// TODO specialize for efficiency
trait AsyncReduction[K, V] extends Log {
  self: AsyncWithRange[K, V] =>

  val defaultBufferOngoing = 5.seconds

  implicit def _keyType = keyType

  implicit def _valueType = valueType

  /** Reduce a stream piecewise, based a partitioning and a reduction function.
    * The main work of reduction is done on each DataStream, this classes' job is
    * to select the appropriate stream reductions, and manage the initial/ongoing
    * parts of this TwoPartStream.
    */
  def flexibleReduce // format: OFF
      ( futureGrouping: Future[ReductionGrouping],
        reduction: Reduction[V],
        ongoingDuration: Option[FiniteDuration] )
      ( implicit execution: ExecutionContext, parentSpan: Span )
      : Future[AsyncWithRange[K,Option[V]]] = { // format: ON

    val bufferOngoing = ongoingDuration getOrElse defaultBufferOngoing

    val futureStream =
      futureGrouping.map { group =>
        // depending on the request parameters, summarize the stream appropriately
        group.grouping match {
          case None =>
            val start = self.requestRange.flatMap(_.start)
            reduceToOnePart(reduction, start, bufferOngoing)
          case Some(ByDuration(periodWithZone)) =>
            reduceByPeriod(periodWithZone, reduction, group.maxParts, bufferOngoing)
          case Some(ByCount(count)) =>
            reduceByElementCount(count, reduction, group.maxParts, bufferOngoing)
          case Some(IntoCountedParts(count)) => ???
          case Some(IntoDurationParts(count)) => ???
        }
      }
    futureStream
//      case (Some(_), Some(_), _) =>
//        val err = ReductionParameterError("both count and period specified")
//        AsyncWithRange.error(err, self.requestRange)
  }

  private def intoCountedParts  // format: OFF
      ( count: Int,
        bufferOngoing: FiniteDuration )
      ( implicit executionContext:ExecutionContext, parentSpan:Span )
      : AsyncWithRange[K, Option[V]] = { // format: ON

    ???
  }


    /** Partition the key range by period, starting with the rounded time of the first key
    * in the stream. Return a reduced stream, with the values in each partition
    * reduced by a provided function. The keys in the reduced stream are set to
    * the start of each time partition.
    *
    * The ongoing portion of the stream is reduced to periods periodically
    * (every 5 seconds by default).
    */
  private def reduceByPeriod // format: OFF
      ( periodWithZone: PeriodWithZone,
        reduction: Reduction[V],
        maxParts: Int,
        bufferOngoing: FiniteDuration )
      ( implicit executionContext:ExecutionContext, parentSpan:Span )
      : AsyncWithRange[K, Option[V]] = { // format: ON

    RecoverNumeric.tryNumeric[K](keyType) match {
      case Success(numericKey) =>
        implicit val _ = numericKey
        val range = requestRange.getOrElse(SoftInterval(None, None))
        val initialResult = self.initial.reduceByPeriod(periodWithZone, range, reduction,
          maxParts, optPrevious = None)
        val prevStateFuture = initialResult.finishState
        val reducedOngoing =
          ongoing.tumblingReduce(bufferOngoing, prevStateFuture) { (buffer, optState) =>
            buffer.reduceByPeriod(periodWithZone, range, reduction, maxParts, optState)
          }
        new AsyncWithRange(initialResult.reducedStream, reducedOngoing, self.requestRange)
      case Failure(err) => AsyncWithRange.error(err, self.requestRange)
    }
  }

  /** reduce the initial part of the stream to a single value, and reduce the ongoing
    * stream to a single value periodically.
    */
  private def reduceToOnePart // format: OFF
      ( reduction: Reduction[V], reduceKey: Option[K] = None,
        bufferOngoing: FiniteDuration )
      ( implicit executionContext:ExecutionContext, parentSpan: Span)
      : AsyncWithRange[K, Option[V]] = { // format: ON

    val initialReduced = initial.reduceToOnePart(reduction, reduceKey)

    val ongoingReduced =
      ongoing.tumblingReduce(bufferOngoing) { (buffer, optState: Option[_]) =>
        val reducedStream = buffer.reduceToOnePart(reduction.newInstance(), None)
        ReductionResult.simple(reducedStream)
      }

    new AsyncWithRange(initialReduced, ongoingReduced, self.requestRange)
  }


  /**
    */
  private def reduceByElementCount // format: OFF
      ( targetCount: Int, reduction: Reduction[V], maxParts: Int,
        bufferOngoing: FiniteDuration )
      ( implicit executionContext:ExecutionContext, parentSpan: Span)
      : AsyncWithRange[K, Option[V]] = { // format: ON

    val initialResult = initial.reduceByElementCount(targetCount, reduction, maxParts)

    val prevStateFuture = initialResult.finishState
    val ongoingReduced =
      ongoing.tumblingReduce(bufferOngoing, prevStateFuture) { (buffer, optState) =>
        buffer.reduceByElementCount(targetCount, reduction.newInstance(), maxParts, optState)
      }

    new AsyncWithRange(initialResult.reducedStream, ongoingReduced, self.requestRange)
  }
}
