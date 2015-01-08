package nest.sparkle.time.transform
import scala.language.higherKinds
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuffer
import nest.sparkle.core.ArrayPair
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
import nest.sparkle.time.transform.ArrayPairsReduction._

// TODO specialize for efficiency
// TODO move to methods on AsyncStream rather than an object
object StreamReduction extends Log {

  /** Reduce a stream piecewise, based a partition function and a reduction function.
    *
    * The stream is partitioned into pieces based on a partition function, and then each
    * piece is reduced based on the reduction function.
    *
    * The partitioning and reduction are combined here to reduce the need to allocate
    * any intermediate data. TODO can this be simplified and still be fast?
    */
  def reduceByOptionalPeriod[K: TypeTag, V: TypeTag] // format: OFF
      ( stream:DataStream[K, V, AsyncWithRange], 
        optPeriod:Option[PeriodWithZone], 
        reduction: Reduction[V])
      ( implicit execution: ExecutionContext)
      : DataStream[K, Option[V], AsyncWithRange] = { // format: ON 

    implicit val keyClassTag = stream.self.keyClassTag
    implicit val valueClassTag = stream.self.valueClassTag

    // depending on the request parameters, summarize the stream appropriately
    (optPeriod, stream.self.requestRange) match {
      case (Some(periodWithZone), Some(rangeInterval)) =>
        // partition the range by period, and producing empty groups as necessary
        // use the start of the partition range as the group key
        //partByPeriod(periodWithZone, rangeInterval)
        ???
      case (Some(periodWithZone), None) =>
        reduceByPeriod(stream, periodWithZone, reduction)
      case (None, Some(rangeInterval)) =>
        // partition everything in one partition
        // use the range start as the group key
        ???
      case (None, None) =>
        reduceToOnePart(stream, reduction)
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
  def reduceByPeriod[K: TypeTag: ClassTag, V: TypeTag: ClassTag] // format: OFF
      ( stream:DataStream[K, V, AsyncWithRange], 
        periodWithZone: PeriodWithZone,
        reduction: Reduction[V],
        bufferOngoing: FiniteDuration = 5.seconds)
      : DataStream[K, Option[V], AsyncWithRange] = { // format: ON

    RecoverNumeric.tryNumeric[K](stream.keyType) match {
      case Success(numericKey) =>
        implicit val _ = numericKey
        val reducedInitial = reduceArrayPairsByPeriod(stream.self.initial, periodWithZone, reduction)
        val reducedOngoing = 
          tumblingReduce(stream.self.ongoing, bufferOngoing) {
            buffer =>
              reduceArrayPairsByPeriod(buffer, periodWithZone, reduction)
          }
        AsyncWithRange(reducedInitial, reducedOngoing, stream.self.requestRange)
      case Failure(err) => AsyncWithRange.error(err, stream.self.requestRange)
    }
  }
  /** reduce the initial part of the stream to a single value, and reduce the ongoing
    * stream to a single value every 5 seconds.
    */
  def reduceToOnePart[K: ClassTag: TypeTag, V: TypeTag] // format: OFF
        ( stream:DataStream[K, V, AsyncWithRange], 
          reduction: Reduction[V],
          bufferOngoing: FiniteDuration = 5.seconds)
        : AsyncWithRange[K, Option[V]] = { // format: ON

    val initialReduced = reduceArrayPairsToOnePart(stream.self.initial, reduction)

    val ongoingReduced = 
      tumblingReduce(stream.self.ongoing, bufferOngoing) {
        buffer =>
          reduceArrayPairsToOnePart(buffer, reduction)
      }

    AsyncWithRange(initialReduced, ongoingReduced, stream.self.requestRange)
  }

}

/** a way to combine two key-value pairs. The function must be associative.
  * (i.e. a semigroup binary operation for pairs)
  */
trait Reduction[V] {
  def plus(aggregateValue: V, newValue: V): V
}

/** combine key value pairs by adding them together */
case class ReduceSum[V: Numeric]() extends Reduction[V] {
  override def plus(aggregateValue: V, newValue: V): V = {
    aggregateValue + newValue
  }
}

