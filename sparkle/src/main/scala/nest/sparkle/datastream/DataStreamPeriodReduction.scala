package nest.sparkle.datastream

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

import org.joda.time.{Interval => JodaInterval}
import rx.lang.scala.{Notification, Observable}
import spire.implicits._
import spire.math.Numeric

import nest.sparkle.measure.Span
import nest.sparkle.util.BooleanOption._
import nest.sparkle.util.{Log, PeriodWithZone}

/** Result of reducing a stream of data. Includes a stream of reduced results and
  * a state object that can be used to resume the reduction on a subsequent stream. */
case class PeriodsResult[K,V]
    ( reducedStream:DataStream[K,Option[V]],
      override val finishState: Future[Option[FrozenProgress[K,V]]] )
    extends ReductionResult[K,V,FrozenProgress[K,V]]

/** Saved state to continue the reduction between streams */
case class FrozenProgress[K, V](periodProgress: PeriodProgress[K], reductionProgress: IncrementalReduction[V])

/** functions for reducing a DataStream by time period */
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
    * Note that the key data is interpreted as epoch milliseconds. LATER make this configurable.
    *
    * @param maxPeriods reduce into at most this many time periods
    */
  def reduceByPeriod // format: OFF
      ( periodWithZone: PeriodWithZone,
        range: SoftInterval[K],
        reduction: IncrementalReduction[V],
        emitEmpties: Boolean,
        maxPeriods: Int = defaultMaxPeriods,
        optPrevious: Option[FrozenProgress[K, V]] = None)
      ( implicit numericKey: Numeric[K], parentSpan:Span )
      : PeriodsResult[K,V] = { // format: ON

    val state = new State(periodWithZone, reduction, range, maxPeriods, optPrevious, emitEmpties)
    val finishState = Promise[Option[FrozenProgress[K,V]]]()
    val reduced = data.materialize.flatMap { notification =>
      notification match {
        case Notification.OnNext(dataArray) =>
          val produced = state.processArray(dataArray)
          Observable.from(produced)
        case Notification.OnCompleted =>
          finishState.complete(Success(Some(state.frozenState)))
          state.remaining match {
            case Some(remaining) => Observable.from(Seq(remaining))
            case None            => Observable.empty
          }
        case Notification.OnError(err) =>
          finishState.complete(Failure(err))
          Observable.error(err)
      }
    }

    val reducedStream = new DataStream(reduced)
    PeriodsResult(reducedStream, finishState.future)
  }



  /** holds the accumulated reduction results */
  private class Results {
    private val keys = ArrayBuffer[K]()
    private val values = ArrayBuffer[Option[V]]()

    /** store a key,Option[value] pair in the results buffer */
    def add(key:K, value:Option[V]): Unit = {
      keys += key
      values += value
    }

    def length: Int = keys.length

    def dataArray: Option[DataArray[K,Option[V]]] =
      (length > 0).toOption.map { _ => DataArray(keys.toArray, values.toArray) }

  }

  /** Maintains the state while reducing a sequence of data arrays. The caller
    * should call processArray for each block, and then remaining when the sequence
    * is complete to fetch any partially reduced data. */
  private class State
      ( periodWithZone: PeriodWithZone,
        reduction2: IncrementalReduction[V],
        range: SoftInterval[K],
        maxPeriods: Int,
        optPrevious: Option[FrozenProgress[K, V]],
        emitEmptyPeriods: Boolean)
      ( implicit numericKey: Numeric[K] ) {

    var periods: Option[PeriodProgress[K]] = None
    var newDataReceived = false // true if we've received new data (not just applied frozen state)
    private var results = new Results()
    var currentReduction = reduction2

    optPrevious match {
      case Some(previousProgress) =>
        periods = Some(previousProgress.periodProgress)
        currentReduction = previousProgress.reductionProgress
      case None =>
        range.start.foreach { key => startPeriodProgress(key) }
    }

    /** walk through all of the elements in this DataArray block. As we go,
      * we'll advance the period iterator as necessary. Returns a data array
      * with an optional reduced value for each time period covered by the DataArray.
      */
    def processArray(dataArray: DataArray[K, V]): Option[DataArray[K, Option[V]]] = {

      /** walk through all the elemnts and return the results */
      def processPairs(): Option[DataArray[K, Option[V]]] = {
        dataArray foreachPair processPair
        if (dataArray.nonEmpty) {
          newDataReceived = true
        }
        takeCompleted()
      }

      if (!dataArray.isEmpty) {
        if (periods.isEmpty) {
          startPeriodProgress(dataArray.keys.head) match {          
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

    /** optionally return the current total from the last period and/or blanks
      * until the requested range is finished.  The current state (partial total
      * and period iteration is unaffected. */
    def remaining: Option[DataArray[K, Option[V]]] = {
      if (newDataReceived) {
        val interimResults = new Results()
        val optTotal = currentReduction.currentTotal
        optTotal.foreach { _ =>
          // emit the interim total even though the period didn't complete.
          // subsequent processing will re-emit data at this key
          interimResults.add(periods.get.start, optTotal)

        }
        // get a copy of the period iterator at the same position
        val interimPeriods = periods.get.duplicate()

        if (emitEmptyPeriods) {
          range.until.foreach { until =>
            var done = false
            while (interimPeriods.end < until && !done) {
              done = !interimPeriods.toNextPeriod()
              interimResults.add(interimPeriods.start, None)
            }
          }
        }
        interimResults.dataArray
      } else {
        None
      }
    }


    /** return enough state to continue interim processing on a new stream */
    def frozenState: FrozenProgress[K,V] = {
      FrozenProgress(periods.get, currentReduction)
    }

    /** initialize time period iterator */
    private def startPeriodProgress(key: K): Boolean = {
      periods = Some(new PeriodProgress(periodWithZone, maxPeriods, key, range.until))
      periods.get.toNextPeriod()
    }

    /** optionally return a data array containing the reduced total for each time period. */
    private def takeCompleted():Option[DataArray[K,Option[V]]] = {
      val dataResults = results.dataArray
      results = new Results
      dataResults
    }


    /** walk through all of the elements in this DataArray block. As we go,
      * we'll advance the period iterator as necessary. we reduce all
      * elements in the current period to a single value. We emit a None
      * value for periods with no elements via emitUntilKeyInPeriod.
      */
    private def processPair(key:K, value:V): Unit = {
      if (key >= periods.get.start && key < periods.get.end) {
        // accumulate a total for this period until we get to the end of the period
        accumulate(value)
      } else if (key >= periods.get.end && !periods.get.complete) {
        // we're now past the period, so emit a value for the previous period
        // and emit a None value for any gap periods until we get to the period containing the key
        advanceUntilPeriodContains(key)

        // unless we ran out of periods, our key should now be in the period
        if (key >= periods.get.start && key < periods.get.end) {
          accumulate(value)
        }
      } else if (key < periods.get.start) {
        log.error(s"processRemainingPairs: unexpected key. bug? $key is < ${periods.get.start}")
      }
    }

    /** merge an a new value into the aggregate total for this period */
    private def accumulate(value: V) {
      currentReduction.accumulate(value)
    }

    /** Advance through time periods as necessary until we get to the period containing the key.
      * For each complete period, emit a value into the results buffer. The value
      * is the optional aggregate total for the period (None if there were no values).
      */
    private def advanceUntilPeriodContains(key: K) {
      assert(key >= periods.get.end)  // we're only called if the key is ahead of the period
      var done = false
      while (!done) {
        val value = finishAccumulation()
        if (value.nonEmpty || emitEmptyPeriods) {
          results.add(periods.get.start, value)
        }
        val nextPeriodExists = periods.get.toNextPeriod()
        if (!nextPeriodExists || key < periods.get.end) {
          done = true
        }
      }
    }

    /** complete the accumulation for this period, returning an aggregate total
      *  if there is one for this period. Aggregation resets, the next accumulation
      *  will replace the current total with a new value (presumably for a new period).  */
    private def finishAccumulation(): Option[V] = {
      val total = currentReduction.currentTotal
      currentReduction = currentReduction.newInstance
      total
    }
  }

}

/** tracks progress in iterating through time periods */
case class PeriodProgress[K]
    ( periodWithZone: PeriodWithZone,
      maxPeriods:Int,
      targetStart:K,
      until:Option[K] )
    ( implicit numericKey:Numeric[K] ) {
  var start: K = 0.asInstanceOf[K]
  var end: K = 0.asInstanceOf[K]
  var complete = false  // true if iteration has reached 'until' or maxPeriods
  var periodsUsed = 0 // total number of time periods visited (to compare against maxPeriods)
  private var periodsIterator: Iterator[JodaInterval] =      // iterates through time periods
    PeriodGroups.jodaIntervals(periodWithZone, targetStart)

  /** advance period iteration to the next time period
    * return true if the iterator was advanced, false if iteration has reached its end */
  def toNextPeriod(): Boolean = {

    def clipToRequestedEnd(proposedEndMillis:Long):K = {
      val proposedEnd = numericKey.fromLong(proposedEndMillis)
      until match {
        case Some(untilEnd) if untilEnd < proposedEnd => untilEnd
        case _                                        => proposedEnd
      }
    }

    def reachedRequestedEnd:Boolean = {
      until match {
        case Some(untilEnd) if started && end >= untilEnd  => true
        case _                                             => false
      }
    }

    if (periodsIterator.hasNext && periodsUsed < maxPeriods && !reachedRequestedEnd) {
      val interval = periodsIterator.next()
      start = numericKey.fromLong(interval.getStartMillis)
      end = clipToRequestedEnd(interval.getEndMillis)
      periodsUsed += 1
      true
    } else {
      complete = true
      false
    }
  }

  /** return a copy of this PeriodProgress at the same iteration state */
  def duplicate(): PeriodProgress[K] = {
    val dup = this.copy()
    dup.toNextPeriod
    dup.advanceTo(start)
    dup
  }


  /** move period iteration forward until it reaches a period containing the provided key
    * unless iteration is blocked by maxPeriods. returns true if the the advance is successful */
  private def advanceTo(key:K): Boolean = {
    var unBlocked = true
    while (start < key && unBlocked) {
      unBlocked = toNextPeriod()
    }
    unBlocked
  }

  private def started: Boolean = periodsUsed > 0


  //    /** return a freeze-dried copy of the current period iteration and total accumulation,
  //      * (so that we can reconstruct the current state of progress on a later stream).
  //      */
  //    def frozen[F](reductionProgress:F):FrozenProgress[K,F] = {
  //      FrozenProgress(periodsIterator, periodsUsed, start, end, reductionProgress)
  //    }

  //    /** return a new PeriodProgress based on the current state combined with a previously frozen one */
  //    def unfreeze(frozenProgress: FrozenProgress[K,F]): PeriodProgress[F] = {
  //      val copied:PeriodProgress[F] = this.copy()
  //      copied.start = frozenProgress.periodStart
  //      copied.end = frozenProgress.periodEnd
  //      copied.periodsUsed = frozenProgress.periodsUsed
  //      copied.periodsIterator = frozenProgress.periodsIterator
  //      copied
  //    }
}

