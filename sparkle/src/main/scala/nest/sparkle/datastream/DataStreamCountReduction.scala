package nest.sparkle.datastream

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.util.{Success, Failure}

import rx.lang.scala.{Observable, Notification}

import nest.sparkle.util.ReflectionUtil

case class CountProgress[K, V](count:Int, key:Option[K], reductionState:Reduction[V])
case class CountResult[K,V]
    ( reducedStream:DataStream[K,Option[V]],
      override val finishState: Future[Option[CountProgress[K,V]]] )
  extends ReductionResult[K,V,CountProgress[K,V]]

trait DataStreamCountReduction[K,V] {
  self: DataStream[K,V] =>

  /*
  iterate through elements, accumulating count items into the state
   */
  def reduceByElementCount
      ( targetCount:Int, reduction: Reduction[V], maxParts:Int,
        optPrevious: Option[CountProgress[K, V]] = None)
      : CountResult[K, V] = {
    val finishState = Promise[Option[CountProgress[K, V]]]()
    val state = CountState(targetCount, reduction, maxParts, optPrevious)

    val reduced = data.materialize.flatMap {
      case Notification.OnNext(dataArray) =>
        val reduced = state.processArray(dataArray)
        Observable.from(reduced)
      case Notification.OnCompleted =>
        finishState.complete(Success(Some(state.progress)))
        state.remaining match {
          case Some(remaining) => Observable.from(Seq(remaining))
          case None            => Observable.empty
        }
      case Notification.OnError(err) =>
        finishState.complete(Failure(err))
        Observable.error(err)
    }
    val reducedStream = new DataStream(reduced)
    CountResult(reducedStream, finishState.future)
  }

  /** counting state */
  case class CountState
      ( targetCount: Int, reduction:Reduction[V], maxParts:Int,
        optPrevious: Option[CountProgress[K, V]]) {
    var count = optPrevious.map(_.count).getOrElse(0)
    var currentReduction = optPrevious.map(_.reductionState).getOrElse(reduction)
    var currentKey:Option[K] = optPrevious.flatMap(_.key)

    def processArray(dataArray:DataArray[K, V]): Option[DataArray[K,Option[V]]] = {
      val keys = ArrayBuffer[K]()
      val values = ArrayBuffer[Option[V]]()

      dataArray.headOption.map { case (firstKey, _) =>
         var taken = 0
         while (taken < dataArray.length) {

           val take = targetCount - count
           if (currentKey.isEmpty && take > 0) {
             currentKey = Some(dataArray.keys(taken))
           }
           dataArray.values.slice(taken, taken + take).foreach { value =>
             currentReduction.accumulate(value)
           }
           count += take
           if (count == targetCount) {
             count = 0
             keys += currentKey.get
             values += currentReduction.currentTotal
             currentReduction = reduction.newInstance()
             currentKey = None
           }
           taken += take
         }
        DataArray(keys.toArray, values.toArray)
      }
    }

    def remaining:  Option[DataArray[K, Option[V]]] = {
      if (count > 0) {
        Some(DataArray.single(currentKey.get, currentReduction.currentTotal))
      } else {
        None
      }
    }

    def progress = CountProgress(count, currentKey, currentReduction)
  }

}