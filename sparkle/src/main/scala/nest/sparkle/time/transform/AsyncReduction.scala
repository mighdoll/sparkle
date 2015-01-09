package nest.sparkle.time.transform

import scala.language.higherKinds
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuffer
import nest.sparkle.core.DataArray
import scala.reflect.runtime.universe._
import spire.implicits._
import spire.math.Numeric
import nest.sparkle.util.PeriodWithZone
import rx.lang.scala.Subject
import nest.sparkle.util.Log
import rx.lang.scala.Observable
import org.joda.time.{ Interval => JodaInterval }
import nest.sparkle.util.RecoverNumeric
import scala.util.Failure
import scala.util.Success
import nest.sparkle.util.PeekableIterator
import rx.lang.scala.Notification
import scala.concurrent.duration._
import nest.sparkle.time.transform.DataArraysReduction._

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
    * any intermediate data. TODO can this be simplified and still be fast?
    */
  def reduceByOptionalPeriod // format: OFF
      ( optPeriod:Option[PeriodWithZone], 
        reduction: Reduction[V])
      ( implicit execution: ExecutionContext)
      : DataStream[K, Option[V], AsyncWithRange] = { // format: ON 

    // depending on the request parameters, summarize the stream appropriately
    (optPeriod, self.requestRange) match {
      case (Some(periodWithZone), Some(rangeInterval)) =>
        // partition the range by period, and producing empty groups as necessary
        // use the start of the partition range as the group key
        //partByPeriod(periodWithZone, rangeInterval)
        ???
      case (Some(periodWithZone), None) =>
        reduceByPeriod(periodWithZone, reduction)
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
        bufferOngoing: FiniteDuration = 5.seconds)
      : DataStream[K, Option[V], AsyncWithRange] = { // format: ON

    RecoverNumeric.tryNumeric[K](keyType) match {
      case Success(numericKey) =>
        implicit val _ = numericKey
        val reducedInitial = reduceDataArraysByPeriod(self.initial, periodWithZone, reduction)
        val reducedOngoing = 
          tumblingReduce(self.ongoing, bufferOngoing) {
            buffer =>
              reduceDataArraysByPeriod(buffer, periodWithZone, reduction)
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
          bufferOngoing: FiniteDuration = 5.seconds)
        : AsyncWithRange[K, Option[V]] = { // format: ON

    val initialReduced = reduceDataArraysToOnePart(self.initial, reduction)

    val ongoingReduced = 
      tumblingReduce(self.ongoing, bufferOngoing) {
        buffer =>
          reduceDataArraysToOnePart(buffer, reduction)
      }

    AsyncWithRange(initialReduced, ongoingReduced, self.requestRange)
  }

}
