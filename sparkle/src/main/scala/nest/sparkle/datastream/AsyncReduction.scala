package nest.sparkle.datastream

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Success}

import rx.lang.scala.Observable

import nest.sparkle.measure.Span
import nest.sparkle.util.{Log, PeriodWithZone, RecoverNumeric}

case class ReductionParameterError(msg:String) extends RuntimeException(msg)

// TODO specialize for efficiency
trait AsyncReduction[K,V] extends Log {
  self: AsyncWithRange[K,V] =>

  implicit def _keyType = keyType
  implicit def _valueType = valueType
  /** Reduce a stream piecewise, based a partitioning and a reduction function.
    * The main work of reduction is done on each DataStream, this classes' job is
    * to select the appropriate stream reductions, and manage the initial/ongoing
    * parts of this TwoPartStream.
    */
  def flexibleReduce // format: OFF
      ( optPeriod:Option[PeriodWithZone],
        optCount: Option[Int],
        reduction: Reduction[V],
        maxParts: Int )
      ( implicit execution: ExecutionContext, parentSpan:Span )
      : TwoPartStream[K, Option[V], AsyncWithRange] = { // format: ON

    // depending on the request parameters, summarize the stream appropriately
    (optCount, optPeriod, self.requestRange) match {
      case (None, Some(periodWithZone), _) =>
        reduceByPeriod(periodWithZone, reduction, maxParts = maxParts)
      case (None, None, Some(rangeInterval)) =>
        reduceToOnePart(reduction, rangeInterval.start)
      case (None, None, None) =>
        reduceToOnePart(reduction)
      case (Some(count), None, _) =>
        reduceByCount(count, reduction, maxParts = maxParts)
      case (Some(_), Some(_), _) =>
        val err = ReductionParameterError("both count and period specified")
        AsyncWithRange.error(err, self.requestRange)
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
  private def reduceByPeriod // format: OFF
      ( periodWithZone: PeriodWithZone,
        reduction: Reduction[V],
        bufferOngoing: FiniteDuration = 5.seconds,
        maxParts: Int )
      ( implicit parentSpan:Span )
      : TwoPartStream[K, Option[V], AsyncWithRange] = { // format: ON

    RecoverNumeric.tryNumeric[K](keyType) match {
      case Success(numericKey) =>
        implicit val _ = numericKey
        val range = requestRange.getOrElse(SoftInterval(None,None))
        val reducedInitial = self.initial.reduceByPeriod(periodWithZone, range, reduction, maxParts)
        val reducedOngoing =
          self.ongoing.tumblingReduce(bufferOngoing) { buffer =>
            buffer.reduceByPeriod(periodWithZone, range, reduction, maxParts)
          }
        AsyncWithRange(reducedInitial, reducedOngoing, self.requestRange)
      case Failure(err) => AsyncWithRange.error(err, self.requestRange)
    }
  }

  /** reduce the initial part of the stream to a single value, and reduce the ongoing
    * stream to a single value every 5 seconds.
    */
  private def reduceToOnePart // format: OFF
        ( reduction: Reduction[V], reduceKey: Option[K] = None,
          bufferOngoing: FiniteDuration = 5.seconds )
        ( implicit parentSpan: Span)
        : AsyncWithRange[K, Option[V]] = { // format: ON

    val initialReduced = initial.reduceToOnePart(reduction, reduceKey)

    val ongoingReduced =
      ongoing.tumblingReduce(bufferOngoing) {
        _.reduceToOnePart(reduction)
      }

    AsyncWithRange(initialReduced, ongoingReduced, self.requestRange)
  }

  private def reduceByCount
      ( count:Int,
        reduction: Reduction[V],
        bufferOngoing: FiniteDuration = 5.seconds,
        maxParts: Int )
      ( implicit parentSpan: Span )
      : TwoPartStream[K, Option[V], AsyncWithRange] = { // format: ON

    def toOptionValues(stream:DataStream[K,V]):DataStream[K, Option[V]] = {
      val optioned:Observable[DataArray[K, Option[V]]] =
        stream.data.map { data =>
          val someValues = data.values.map(Some(_):Option[V])
          DataArray(data.keys, someValues)
        }
      DataStream(optioned)
    }

    val initialReduced = initial.reduceToParts(count, reduction)
    val initialOptions = toOptionValues(initialReduced)

    val ongoingReduced =
      ongoing.tumblingReduce(bufferOngoing) {
        _.reduceToOnePart(reduction)
      }

    AsyncWithRange(initialOptions, ongoingReduced, self.requestRange)
  }
}
