package nest.sparkle.datastream

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success}

import nest.sparkle.measure.Span
import nest.sparkle.util.{Log, PeriodWithZone, RecoverNumeric}

// TODO specialize for efficiency
trait AsyncReduction[K,V] extends Log {
  self: AsyncWithRange[K,V] =>

  implicit def _keyType = keyType
  implicit def _valueType = valueType
  /** Reduce a stream piecewise, based a partition function and a reduction function.
    *
    * The stream is partitioned into pieces based on a partition function, and then each
    * piece is reduced based on the reduction function.
    *
    * The partitioning and reduction are combined here to reduce the need to allocate
    * any intermediate data.
    */
  def reduceByOptionalPeriod // format: OFF
      ( optPeriod:Option[PeriodWithZone], 
        reduction: Reduction[V],
        maxParts: Int )
      ( implicit execution: ExecutionContext, parentSpan:Span )
      : TwoPartStream[K, Option[V], AsyncWithRange] = { // format: ON 

    // depending on the request parameters, summarize the stream appropriately
    (optPeriod, self.requestRange) match {
      case (Some(periodWithZone), Some(rangeInterval)) =>
        // partition the range by period, and producing empty groups as necessary
        // use the start of the partition range as the group key
        //partByPeriod(periodWithZone, rangeInterval)
        ???
      case (Some(periodWithZone), None) =>
        reduceByPeriod(periodWithZone, reduction, maxParts = maxParts)
      case (None, Some(rangeInterval)) =>
        // partition everything in one partition
        // use the range start as the group key
        ???
      case (None, None) =>
        reduceToOnePart(reduction)
    }
  }

  /** Partition the key range by period, starting with the rounded time of the first key
    * in the stream. Return a reduced stream, with the values in each partition
    * reduced by a provided function. The keys in the reduced stream are set to
    * the start of each time partition.
    *
    * The ongoing portion of the stream is reduced to periods periodically
    * (every 5 seconds by default).
    */
  def reduceByPeriod // format: OFF
      ( periodWithZone: PeriodWithZone,
        reduction: Reduction[V],
        bufferOngoing: FiniteDuration = 5.seconds,
        maxParts: Int )
      ( implicit parentSpan:Span )
      : TwoPartStream[K, Option[V], AsyncWithRange] = { // format: ON

    RecoverNumeric.tryNumeric[K](keyType) match {
      case Success(numericKey) =>
        implicit val _ = numericKey
        val reducedInitial = self.initial.reduceByPeriod(periodWithZone, reduction, maxParts)
        val reducedOngoing = 
          self.ongoing.tumblingReduce(bufferOngoing) { buffer =>
            buffer.reduceByPeriod(periodWithZone, reduction, maxParts)
          }
        AsyncWithRange(reducedInitial, reducedOngoing, self.requestRange)
      case Failure(err) => AsyncWithRange.error(err, self.requestRange)
    }
  }

  /** reduce the initial part of the stream to a single value, and reduce the ongoing
    * stream to a single value every 5 seconds.
    */
  def reduceToOnePart // format: OFF
        ( reduction: Reduction[V],
          bufferOngoing: FiniteDuration = 5.seconds )
        ( implicit parentSpan: Span)
        : AsyncWithRange[K, Option[V]] = { // format: ON

    val initialReduced = initial.reduceToOnePart(reduction)

    val ongoingReduced = 
      ongoing.tumblingReduce(bufferOngoing) {
        _.reduceToOnePart(reduction)
      }

    AsyncWithRange(initialReduced, ongoingReduced, self.requestRange)
  }

}
