package nest.sparkle.time.transform

import scala.reflect.ClassTag
import nest.sparkle.util.PeriodWithZone
import rx.lang.scala.Observable
import nest.sparkle.util.PeekableIterator
import nest.sparkle.core.ArrayPair
import scala.collection.mutable.ArrayBuffer
import rx.lang.scala.Notification
import org.joda.time.{ Interval => JodaInterval }
import spire.math.Numeric
import spire.implicits._
import nest.sparkle.util.Log
import scala.reflect.runtime.universe._
import scala.concurrent.duration._

/** Functions for reducing an Observable of array pairs to smaller ArrayPairs
  */
// LATER move these to methods on an object that wraps Observable[ArrayPair[K,V]]. PairStream?
object ArrayPairsReduction extends Log {

  /** Reduce the array pair array to a single pair. A reduction function is applied
    * to reduce the pair values to a single value. The key of the returned pair
    * is the first key of original stream.
    */
  def reduceArrayPairsToOnePart[K: ClassTag: TypeTag, V: TypeTag] // format: OFF
      ( observablePairs: Observable[ArrayPair[K,V]], 
        reduction: PairReduction[V])
      : Observable[ArrayPair[K, Option[V]]] = { // format: ON

    // buffer the first value so we can extract the first key
    val obsPairs = observablePairs.replay(1)
    obsPairs.connect

    // observable containing a single reduced value
    val reducedValue = {
      val optionPerPairsArray = obsPairs.map { pairs =>
        pairs.valuesReduceLeftOption(reduction.plus)
      }
      reduceOptions(optionPerPairsArray, reduction)
    }

    // observable containing the single first key (or empty)
    val firstKey: Observable[K] = {
      obsPairs.head.map { pairs =>
        // we're counting on the first array being nonempty.. LATER relax this assumption
        pairs.headOption.get match { case (k, v) => k }
      }
    }

    // combine key and value together into an ArrayPair
    val reduced: Observable[ArrayPair[K, Option[V]]] = {
      firstKey.zip(reducedValue).map {
        case (key, value) =>
          ArrayPair.single(key, Some(value))
      }
    }

    reduced
  }

  /** reduce optional values with a reduction function. */ // scalaz would make this automatic..
  private def reduceOptions[V] // format: OFF
      ( optPairs: Observable[Option[V]], 
        reduction: PairReduction[V] ) // format: ON
        : Observable[V] = {

    val optionalResult = optPairs.reduce { (optA, optB) =>
      (optA, optB) match {
        case (a@Some(_), None) => a
        case (None, b@Some(_)) => b
        case (None, None)      => None
        case (Some(aValue), Some(bValue)) =>
          val reduced = reduction.plus(aValue, bValue)
          Some(reduced)
      }
    }
    optionalResult.flatMap { option => Observable.from(option) }
  }

  /** apply a reduction function to a time-window of of array pairs */
  def tumblingReduce[K, V] // format: OFF
      ( ongoing:Observable[ArrayPair[K,V]], bufferOngoing: FiniteDuration )
      ( reduceFn: Observable[ArrayPair[K,V]] => Observable[ArrayPair[K, Option[V]]] )
      : Observable[ArrayPair[K, Option[V]]] = { // format: ON

    for {
      buffer <- ongoing.tumbling(bufferOngoing)
      reduced <- reduceFn(buffer)
      nonEmptyReduced <- if (reduced.isEmpty) Observable.empty else Observable.from(Seq(reduced))
    } yield {
      nonEmptyReduced
    }
  }

  /** Reduce an Observable of ArrayPairs into a smaller Observable by dividing
    * the pair data into partitions based on joda time period, and reducing
    * each partition's pair data with a supplied reduction function. The keys
    * for each partition are the start of the period.
    *
    * The values for each partition are returned optionally, None is returned
    * for partitions that contain no pair data.
    *
    * Note that the key data is intepreted as epoch milliseconds. LATER make this configurable.
    */
  def reduceArrayPairsByPeriod[K: ClassTag, V: ClassTag] // format: OFF
      ( observablePairs: Observable[ArrayPair[K, V]],
        periodWithZone: PeriodWithZone,
        reduction: PairReduction[V] )
      ( implicit numericKey: Numeric[K] )
      : Observable[ArrayPair[K, Option[V]]] = { // format: ON

    val periodState = new PeriodState[K, V](reduction)

    observablePairs.materialize.flatMap { notification =>
      val pairsState = new PairsState(periodState)

      notification match {
        case Notification.OnCompleted =>
          periodState.currentAccumulation() match {
            case Some(pair) => Observable.from(Seq(pair))
            case None       => Observable.empty
          }

        case Notification.OnError(err) =>
          Observable.error(err)

        case Notification.OnNext(arrayPairs) =>
          val pairs = PeekableIterator(arrayPairs.iterator)
          val reducedPairs = pairsState.reduceCompletePeriods(pairs, periodWithZone, reduction)
          Observable.from(Seq(reducedPairs))
      }

    }
  }
}

/** maintains the state needed while iterating through time periods */
private class PeriodState[K: Numeric: ClassTag, V](reduction: PairReduction[V]) {
  var accumulationStarted = false
  var currentTotal: V = 0.asInstanceOf[V]
  var periodStart: K = 0.asInstanceOf[K]
  var periodEnd: K = 0.asInstanceOf[K]

  private var periods: Iterator[JodaInterval] = null
  private val numericKey = implicitly[Numeric[K]]

  /** if period iteration hasn't already begun, start the period iteration
    * beginning with the time period containing the key
    */
  def begin(key: K, periodWithZone: PeriodWithZone) {
    if (periods == null) {
      periods = PeriodPartitioner.jodaIntervals(periodWithZone, key)
      toNextPeriod()
    }
  }

  /** advance period iteration to the next time period */
  def toNextPeriod(): Boolean = {
    if (periods.hasNext) {
      val interval = periods.next()
      periodStart = numericKey.fromLong(interval.getStartMillis)
      periodEnd = numericKey.fromLong(interval.getEndMillis)
      true
    } else {
      false
    }
  }

  /** merge an a new value into the aggregate total for this period */
  def accumulate(value: V) {
    if (accumulationStarted) {
      currentTotal = reduction.plus(currentTotal, value)
    } else {
      accumulationStarted = true
      currentTotal = value
    }
  }

  /** complete the accumulation for this period, returning an aggregate total
   *  if there is one for this period. Aggregation resets, the next accumulation
   *  will replace the current total with a new value (presumably for a new period).  */
  def finishAccumulation(): Option[V] = {
    if (accumulationStarted) {
      accumulationStarted = false
      Some(currentTotal)
    } else {
      None
    }
  }

  /** return the current aggregate total for this period, but do not reset aggregation */
  def currentAccumulation(): Option[ArrayPair[K, Option[V]]] = {
    if (accumulationStarted) {
      Some(ArrayPair.single(periodStart, Some(currentTotal)))
    } else {
      None
    }
  }
}

/** Maintains the state while iterating through each time-value pair */
private class PairsState[K: Numeric: ClassTag, V](periodState: PeriodState[K, V]) extends Log {
  private val resultKeys = ArrayBuffer[K]()
  private val resultValues = ArrayBuffer[Option[V]]()

  /** reduce an array of pairs by period, returning the reduced totals. Accumulate
    * an partial data for the last period in the periodState.
    */
  def reduceCompletePeriods(pairs: PeekableIterator[(K, V)],
                            periodWithZone: PeriodWithZone,
                            reduction: PairReduction[V]): ArrayPair[K, Option[V]] = {

    pairs.headOption.foreach {
      case (firstKey, firstValue) =>
        periodState.begin(firstKey, periodWithZone)
        processPairs(pairs)
    }
    // results arrays are created as a side effect of processRemainingPairs
    ArrayPair(resultKeys.toArray, resultValues.toArray)
  }

  /** walk through all of the elements in this ArrayPair block. As we go,
    * we'll advance the period iterator as necessary. we reduce all
    * elements in the current period to a single value. We emit a None
    * value for periods with no elements.
    */
  private def processPairs(remainingPairs: Iterator[(K, V)]) {
    remainingPairs.foreach {
      case (key, value) =>
        emitUntilKeyInPeriod(key)
        if (key >= periodState.periodStart && key < periodState.periodEnd) {
          periodState.accumulate(value)
        } else if (key < periodState.periodStart) {
          log.error(s"processRemainingPairs: unexpected key. bug? $key is < ${periodState.periodStart}")
        }
    }
  }

  /** Iterate through periods as necessary until we get to the period containing the key.
    * For each complete period, emit a value into the results buffer. The value
    * is the optional aggregate total for the period (None if there were no values).
    */
  private def emitUntilKeyInPeriod(key: K) {
    var done = false
    while (key >= periodState.periodEnd && !done) {
      recordPeriodTotal(periodState.finishAccumulation())

      if (!periodState.toNextPeriod())
        done = true
    }
  }
  
  /** put a key,value in the results array */
  private def recordPeriodTotal(value: Option[V]) {
    resultKeys += periodState.periodStart
    resultValues += value
  }


}