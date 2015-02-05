package nest.sparkle.datastream

import scala.collection.mutable.ArrayBuffer

import org.joda.time.Interval
import rx.lang.scala.{Observable, Notification}
import spire.math.Numeric

import nest.sparkle.measure.Span
import nest.sparkle.util.TimeValueString.timeValueToString
import nest.sparkle.util.{Log, PeriodWithZone}
import org.joda.time.{Interval => JodaInterval}
import scala.reflect.runtime.universe._
import spire.implicits._

trait DataStreamPeriodReduction[K,V] extends Log {
  self:DataStream[K,V] =>
  implicit def keyTypeTag_ = keyTypeTag
  implicit def valueTypeTag_ = valueTypeTag

  val defaultMaxPeriods = 1000
  /** Reduce an Observable of DataArrays into a smaller Observable by dividing
    * the pair data into partitions based on joda time period, and reducing
    * each partition's pair data with a supplied reduction function. The keys
    * for each partition are the start of the period.
    *
    * The values for each partition are returned optionally, None is returned
    * for partitions that contain no pair data.
    *
    * Note that the key data is intepreted as epoch milliseconds. LATER make this configurable.
    *
    * @param maxPeriods reduce into at most this many time periods
    */
  def reduceByPeriod // format: OFF
  ( periodWithZone: PeriodWithZone,
    range: SoftInterval[K],
    reduction: Reduction[V],
    maxPeriods: Int = defaultMaxPeriods)
  ( implicit numericKey: Numeric[K], parentSpan:Span )
  : DataStream[K, Option[V]] = { // format: ON

    var state = new State(periodWithZone, reduction, range, maxPeriods)
    val reduced = data.materialize.flatMap { notification =>
      notification match {
        case Notification.OnNext(dataArray) =>
          val produced = state.processArray(dataArray)
          Observable.from(produced)
        case Notification.OnCompleted =>
          state.remaining match {
            case Some(remaining) => Observable.from(Seq(remaining))
            case None            => Observable.empty
          }
        case Notification.OnError(err) =>
          Observable.error(err)
      }
    }

    new DataStream(reduced)
  }

  /** Maintains the state while reducing a sequence of data arrays. The caller
    * should call processArray for each block, and then remaining when the sequence
    * is complete to fetch any partially reduced data. */
  class State( periodWithZone: PeriodWithZone, reduction: Reduction[V],
               range:SoftInterval[K], maxPeriods: Int )
             ( implicit numericKey:Numeric[K] ) {
    var started = false
    var accumulationStarted = false
    var currentTotal: V = 0.asInstanceOf[V]
    var periodStart: K = 0.asInstanceOf[K]
    var periodEnd: K = 0.asInstanceOf[K]
    var periodsUsed = 0
    private var periods: Iterator[JodaInterval] = null
    private val resultKeys = ArrayBuffer[K]()
    private val resultValues = ArrayBuffer[Option[V]]()

    range.start.foreach{key =>
      startAt(key)
      toNextPeriod()
    }

    /** walk through all of the elements in this DataArray block. As we go,
      * we'll advance the period iterator as necessary. Returns a data array
      * with an optional reduced value for each time period covered by the DataArray.
      */
    def processArray(dataArray:DataArray[K,V]):Option[DataArray[K, Option[V]]] = {

      /** walk through all the elemnts and return the results */
      def processPairs(): Option[DataArray[K, Option[V]]] = {
        dataArray foreachPair processPair
        takeCompleted()
      }

     if (!dataArray.isEmpty) {
        if (!started) {
          startAt(dataArray.keys.head)
          toNextPeriod() match {
            case true  => processPairs()
            case false => None
          }
        } else {
          processPairs()
        }
      } else {
        None
      }
    }

    /** optionally return the current total from the last period */
    def remaining:Option[DataArray[K,Option[V]]] = {
      if (accumulationStarted) {
        // emit the interim total even though the period didn't complete.
        // subsequent processing (e.g. from an ongoing data stream) should reuse
        // this accumulated state, but currently doesn't.
        // TODO propagate the State between reductions
        // for now, we're done anyway, so ok to pretend this partial value is complete
        emit(Some(currentTotal))
      }
      range.until.foreach { until =>
        var done = false
        while (periodEnd < until && !done) {
          done = !toNextPeriod()
          emit(None)
        }
      }
      takeCompleted()
    }

    /** initialize time period iterator */
    private def startAt(key: K): Unit = {
      started = true
      periods = PeriodGroups.jodaIntervals(periodWithZone, key)
    }

    /** optionally return a data array containing the reduced total for each time period. */
    private def takeCompleted():Option[DataArray[K,Option[V]]] = {
      if (resultKeys.length > 0) {
        val result = DataArray(resultKeys.toArray, resultValues.toArray)
        resultKeys.clear()
        resultValues.clear()
        Some(result)
      } else {
        None
      }
    }


    /** advance period iteration to the next time period */
    private def toNextPeriod(): Boolean = {
//      def pastDefinedEnd():Boolean = {
//        optEnd match {
//          case None => false
//          case Some(end) if (end)
//        }
//      }

      def clipToRequestedEnd(proposedEnd:K):K = {
        range.until match {
          case Some(until) if until < proposedEnd => until
          case _ => proposedEnd
        }
      }

      if (periods.hasNext && periodsUsed < maxPeriods) {
        val interval = periods.next()
        periodStart = numericKey.fromLong(interval.getStartMillis)
        periodEnd = clipToRequestedEnd(numericKey.fromLong(interval.getEndMillis))
        periodsUsed += 1
        true
      } else {
        false
      }
    }


    /** walk through all of the elements in this DataArray block. As we go,
      * we'll advance the period iterator as necessary. we reduce all
      * elements in the current period to a single value. We emit a None
      * value for periods with no elements via emitUntilKeyInPeriod.
      */
    private def processPair(key:K, value:V): Unit = {
//      println(s"processPair ${timeValueToString(key.asInstanceOf[Long], value)}")
      if (key >= periodStart && key < periodEnd) {
        // accumulate a total for this period until we get to the end of the period
        accumulate(value)
      } else if (key >= periodEnd) {
        // we're now past the period, so emit a value for the previous period
        // and emit a None value for any gap periods until we get to the period containing the key
        emitUntilKeyInPeriod(key)

        // unless we ran out of periods, our key should now be in the period
        if (key >= periodStart && key < periodEnd) {
          accumulate(value)
        }
      } else if (key < periodStart) {
        log.error(s"processRemainingPairs: unexpected key. bug? $key is < ${periodStart}")
      }
    }

    /** merge an a new value into the aggregate total for this period */
    private def accumulate(value: V) {
      if (accumulationStarted) {
        currentTotal = reduction.plus(currentTotal, value)
      } else {
        accumulationStarted = true
        currentTotal = value
      }
    }

    /** Advance through time periods as necessary until we get to the period containing the key.
      * For each complete period, emit a value into the results buffer. The value
      * is the optional aggregate total for the period (None if there were no values).
      */
    private def emitUntilKeyInPeriod(key: K) {
      assert(key >= periodEnd)  // we're only called if the key is ahead of the period
      var done = false
      while (!done) {
        val value = finishAccumulation()
        emit(value)
        val nextPeriodExists = toNextPeriod()
        if (!nextPeriodExists || key < periodEnd) {
          done = true
        }
      }
    }

    /** store a key,Option[value] pair in the results buffer */
    private def emit(optionalValue:Option[V]) {
      resultKeys += periodStart
      resultValues += optionalValue
    }

    /** complete the accumulation for this period, returning an aggregate total
      *  if there is one for this period. Aggregation resets, the next accumulation
      *  will replace the current total with a new value (presumably for a new period).  */
    private def finishAccumulation(): Option[V] = {
      if (accumulationStarted) {
        accumulationStarted = false
        Some(currentTotal)
      } else {
        None
      }
    }
  }

}
