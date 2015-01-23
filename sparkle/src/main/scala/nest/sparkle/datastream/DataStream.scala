package nest.sparkle.datastream

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import org.joda.time.{Interval => JodaInterval}
import rx.lang.scala.{Notification, Observable}
import spire.implicits._
import spire.math.Numeric

import nest.sparkle.measure.Span
import nest.sparkle.util.{Log, PeekableIterator, PeriodWithZone, ReflectionUtil}

/** Functions for reducing an Observable of array pairs to smaller DataArrays
  */
// LATER move these to methods on an object that wraps Observable[DataArray[K,V]]. PairStream?
case class DataStream[K: TypeTag, V: TypeTag](data: Observable[DataArray[K,V]]) {

  implicit lazy val keyClassTag = ReflectionUtil.classTag[K](typeTag[K])
  implicit lazy val valueClassTag = ReflectionUtil.classTag[V](typeTag[V])

  /** Reduce the array pair array to a single pair. A reduction function is applied
    * to reduce the pair values to a single value. The key of the returned pair
    * is the first key of original stream.
    */
  def reduceToOnePart
      ( reduction: Reduction[V] )
      ( implicit parentSpan:Span )
      : DataStream[K, Option[V]] = {

    val reducedBlocks:Observable[(K, Option[V])] =
      data.filter(!_.isEmpty).map { pairs =>
        Span("reduceBlock").time {
          val optValue = pairs.valuesReduceLeftOption(reduction.plus)
          (pairs.keys(0), optValue)
        }
      }
    
    val reducedTuple =
      reducedBlocks.reduce { (total, next) =>
        val (key, totalValue) = total
        val (_, newValue) = next
        val newTotal = reduceOption(totalValue, newValue, reduction)
        (key, newTotal)
      }
    
    val reducedArray = reducedTuple.map { case (key, total) => DataArray.single(key, total) }
    DataStream(reducedArray)
  }
   
  /** reduce optional values with a reduction function. */ // scalaz would make this automatic..
  private def reduceOption[T](optA:Option[T], optB:Option[T], reduction:Reduction[T]): Option[T] = {
    (optA, optB) match {
      case (a@Some(_), None) => a
      case (None, b@Some(_)) => b
      case (None, None)      => None
      case (Some(aValue), Some(bValue)) =>
        val reduced = reduction.plus(aValue, bValue)
        Some(reduced)
    }
  }

  /** apply a reduction function to a time-window of of array pairs */
  def tumblingReduce // format: OFF
      ( bufferOngoing: FiniteDuration )
      ( reduceFn: DataStream[K, V] => DataStream[K, Option[V]] )
      : DataStream[K, Option[V]] = { // format: ON

    val reducedStream = 
      for {
        buffer <- data.tumbling(bufferOngoing)
        reduced <- reduceFn(DataStream(buffer)).data
        nonEmptyReduced <- if (reduced.isEmpty) Observable.empty else Observable.from(Seq(reduced))
      } yield {
        nonEmptyReduced
      }
      
    new DataStream(reducedStream)
  }


  
  /** Reduce an Observable of DataArrays into a smaller Observable by dividing
    * the pair data into partitions based on joda time period, and reducing
    * each partition's pair data with a supplied reduction function. The keys
    * for each partition are the start of the period.
    *
    * The values for each partition are returned optionally, None is returned
    * for partitions that contain no pair data.
    *
    * Note that the key data is intepreted as epoch milliseconds. LATER make this configurable.
    */
  def reduceByPeriod // format: OFF
      ( periodWithZone: PeriodWithZone,
        reduction: Reduction[V] )
      ( implicit numericKey: Numeric[K], parentSpan:Span )
      : DataStream[K, Option[V]] = { // format: ON

    val periodState = new PeriodState[K, V](reduction)

    val reduced = data.materialize.flatMap { notification =>
      val pairsState = new PairsState(periodState)

      notification match {
        case Notification.OnNext(dataArray) =>
          val pairs = PeekableIterator(dataArray.iterator)
          val reducedPairs = pairsState.reduceCompletePeriods(pairs, periodWithZone, reduction)
          Observable.from(Seq(reducedPairs))

        case Notification.OnCompleted =>
          periodState.currentAccumulation() match {
            case Some(pair) => Observable.from(Seq(pair))
            case None       => Observable.empty
          }

        case Notification.OnError(err) =>
          Observable.error(err)

      }
    }
    
    new DataStream(reduced)
  }
}


/** Maintains the state needed while iterating through time periods */
private class PeriodState[K: Numeric: ClassTag, V](reduction: Reduction[V]) {
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
      periods = PeriodGroups.jodaIntervals(periodWithZone, key)
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
  def currentAccumulation(): Option[DataArray[K, Option[V]]] = {
    if (accumulationStarted) {
      Some(DataArray.single(periodStart, Some(currentTotal)))
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
    * any partial data for the last period in the periodState.
    */
  def reduceCompletePeriods
      ( pairs: PeekableIterator[(K, V)],
        periodWithZone: PeriodWithZone,
        reduction: Reduction[V] )
      (implicit parentSpan:Span)
      : DataArray[K, Option[V]] = {

    Span("reduceBlock").time {
      pairs.headOption.foreach {
        case (firstKey, firstValue) =>
          periodState.begin(firstKey, periodWithZone)
          processPairs(pairs)
      }

      // results arrays are created as a side effect of processPairs
      DataArray(resultKeys.toArray, resultValues.toArray)
    }
  }

  /** walk through all of the elements in this DataArray block. As we go,
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