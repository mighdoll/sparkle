package nest.sparkle.datastream

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success}

import rx.lang.scala.Observable

import nest.sparkle.measure.Span
import nest.sparkle.util.{Log, PeriodWithZone, RecoverNumeric}


/** client requestable ways to to group items for a reduction operation */
case class RequestedGrouping (
  maxParts: Int,
  grouping: Option[RequestGrouping]
)

/** requested group items for a reduction operation */
case class StreamGrouping (
  maxParts: Int,
  grouping: Option[ReductionGrouping]
)


/** groupings that are processed by the reduction code */
sealed trait ReductionGrouping

/** permitted groupings that may be requested for reductions */
sealed trait RequestGrouping
case class ByDuration(duration:PeriodWithZone, emitEmpties:Boolean)
  extends RequestGrouping with ReductionGrouping
case class ByCount(count:Int) extends RequestGrouping with ReductionGrouping
case class IntoCountedParts(count:Int) extends RequestGrouping  // TODO rename to nPartsByCount
case class IntoDurationParts(count:Int, emitEmpties:Boolean) extends RequestGrouping // TODO rename to nPartsByDuration


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
      ( futureGrouping: Future[StreamGrouping],
        reduction: IncrementalReduction[V],
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
          case Some(ByDuration(periodWithZone, emitEmpties)) =>
            reduceByPeriod(periodWithZone, reduction, emitEmpties, group.maxParts, bufferOngoing)
          case Some(ByCount(count)) =>
            reduceByElementCount(count, reduction, group.maxParts, bufferOngoing)
        }
      }
    futureStream
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
        reduction: IncrementalReduction[V],
        emitEmpties: Boolean,
        maxParts: Int,
        bufferOngoing: FiniteDuration )
      ( implicit executionContext:ExecutionContext, parentSpan:Span )
      : AsyncWithRange[K, Option[V]] = { // format: ON

    RecoverNumeric.tryNumeric[K](keyType) match {
      case Success(numericKey) =>
        implicit val _ = numericKey
        val range = requestRange.getOrElse(SoftInterval(None, None))
        val initialResult = self.initial.reduceByPeriod(periodWithZone, range, reduction,
          emitEmpties, maxParts, optPrevious = None)
        val prevStateFuture = initialResult.finishState

        val reducedOngoing =
          ongoing.tumblingReduce(bufferOngoing, prevStateFuture) { (buffer, optState) =>
            buffer.reduceByPeriod(periodWithZone, range, reduction, emitEmpties, maxParts, optState)
          }
        new AsyncWithRange(initialResult.reducedStream, reducedOngoing, self.requestRange)
      case Failure(err) => AsyncWithRange.error(err, self.requestRange)
    }
  }

  /** reduce the initial part of the stream to a single value, and reduce the ongoing
    * stream to a single value periodically.
    */
  private def reduceToOnePart // format: OFF
      ( reduction: IncrementalReduction[V], reduceKey: Option[K] = None,
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


  /** Reduce the stream into partitions with the same number of elements.
    * The final partition of the initial portion of the stream may have fewer elements,
    * so that visualization clients will can display all the data that is available.
    * The ongoing stream will only contain reductions of the target number of elements,
    * i.e. it will wait until the requisite number of elements show up before producing
    * a reduction.
    */
  private def reduceByElementCount // format: OFF
      ( targetCount: Int, reduction: IncrementalReduction[V], maxParts: Int,
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
