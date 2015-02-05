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
case class DataStream[K: TypeTag, V: TypeTag](data: Observable[DataArray[K,V]])
    extends DataStreamPeriodReduction[K,V] {

  implicit lazy val keyClassTag = ReflectionUtil.classTag[K](typeTag[K])
  implicit lazy val valueClassTag = ReflectionUtil.classTag[V](typeTag[V])
  def keyTypeTag = typeTag[K]
  def valueTypeTag = typeTag[V]

  /** Reduce the array pair array to a single pair. A reduction function is applied
    * to reduce the pair values to a single value. The key of the returned pair
    * is the first key of original stream.
    */
  def reduceToOnePart
      ( reduction: Reduction[V], reduceKey:Option[K] = None)
      ( implicit parentSpan:Span )
      : DataStream[K, Option[V]] = {

    val reducedBlocks:Observable[(K, Option[V])] =
      data.filter(!_.isEmpty).map { pairs =>
        Span("reduceBlock").time {
          val optValue = pairs.values.reduceLeftOption(reduction.plus)
          val key = reduceKey.getOrElse(pairs.keys(0))
          (key, optValue)
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

  object ToPartsState {
    def apply():ToPartsState = ToPartsState(Vector[K](), Vector[V]())
    def apply(pairs:Vector[(K,V)]):ToPartsState = {
      val keys = pairs.map { case (key, value) => key }
      val values = pairs.map { case (key, value) => value }
      ToPartsState(keys, values)
    }

    def reduceGroup(group:Seq[(K,V)], reduction:Reduction[V]): (K,V) = {
      val values = group map { case (key, value) => value }
      val value = values.reduceLeft(reduction.plus)
      val key = group.head match { case (key, value) => key}
      (key, value)
    }
  }

  import ToPartsState.reduceGroup

  case class ToPartsState( resultKeys:Vector[K],
                           resultValues:Vector[V],
                           currentCount:Int = 0,
                           currentKey: Option[K] = None,
                           currentTotal: Option[V] = None
                           ) {
    def withCurrentZero:ToPartsState = ToPartsState(resultKeys, resultValues)

    def mergeGroup(group:Seq[(K,V)], reduction:Reduction[V]): ToPartsState = {
      val (reducedKey, reducedValue) = reduceGroup(group, reduction)
      val key = currentKey.getOrElse(reducedKey)
      val value = currentTotal match {
        case Some(oldTotal) => reduction.plus(reducedValue, oldTotal)
        case None           => reducedValue
      }
      ToPartsState(resultKeys :+ key, resultValues :+ value)
    }

    def remaining:Option[ToPartsState] = {
      for {
        key <- currentKey
        value <- currentTotal
      } yield {
        ToPartsState(Vector(key), Vector(value))
      }
    }

    def resultPairs:Vector[(K,V)] = resultKeys zip resultValues

  }

  /** reduce a stream into count parts */
  def reduceToParts
      ( count: Int, reduction: Reduction[V])
      ( implicit parentSpan:Span )
      : DataStream[K, V] = {

    def reduceBlock(pairs:DataArray[K,V], state:ToPartsState): ToPartsState = {
      Span("reduceBlock").time {
        val pairsVector = pairs.toVector
        val firstGroup = pairsVector.take(count - state.currentCount)
        val firstGroupComplete = firstGroup.length + state.currentCount == count
        val mergedState = state.mergeGroup(firstGroup, reduction)
        lazy val tailGroups = pairsVector.drop(firstGroup.length).grouped(count).toVector
        lazy val lastGroupComplete = tailGroups.isEmpty || tailGroups.last.length == count

        val newState:ToPartsState =
          if (firstGroupComplete) {
            if (lastGroupComplete) {
              val reducedPairs = tailGroups.map(reduceGroup(_, reduction))
              ToPartsState(mergedState.resultPairs ++ reducedPairs)
            } else { // first group complete, last group is incomplete
              val completeGroups = tailGroups.take(tailGroups.length - 1)
              val reducedPairs = completeGroups.map(reduceGroup(_, reduction))
              val reducedState = ToPartsState(mergedState.resultPairs ++ reducedPairs)
              val incompleteLast = tailGroups.last
              val (key, value) = reduceGroup(incompleteLast, reduction)
              reducedState.copy(
                currentCount = incompleteLast.length,
                currentKey = Some(key),
                currentTotal = Some(value)
              )
            }
          } else { // incomplete first group
            mergedState
          }

        newState
      }
    }

    if (count == 0) {
      DataStream.empty
    } else {
      var previousState = ToPartsState()
      val states:Observable[ToPartsState] =
        data.filter(!_.isEmpty).materialize.flatMap { notification =>

          notification match {
            case Notification.OnNext(pairs) =>
              previousState = reduceBlock(pairs, previousState)
              Observable.from(Seq(previousState))
            case Notification.OnCompleted   =>
              val remainingObservable = previousState.remaining.map { remainingState =>
                  Observable.from(Seq(remainingState))
                }
              remainingObservable.getOrElse(Observable.empty)
            case Notification.OnError(err)  =>
              Observable.error(err)
          }
        }

      val dataArrays:Observable[DataArray[K,V]] =
        states.map { state =>
          val keys = state.resultKeys.toArray
          val values = state.resultValues.toArray
          DataArray(keys, values)
        }

      DataStream(dataArrays)
    }
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
  // TODO progagate ToPartsState between each buffer, so the reduction can use data from multiple buffers
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

}

object DataStream {
  def empty[K:TypeTag, V:TypeTag] = DataStream[K,V](Observable.empty)
}