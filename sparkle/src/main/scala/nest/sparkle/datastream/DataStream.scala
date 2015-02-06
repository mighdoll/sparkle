package nest.sparkle.datastream

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._

import rx.lang.scala.{Notification, Observable}

import nest.sparkle.measure.Span
import nest.sparkle.util.ObservableUtil.reduceSafe
import nest.sparkle.util.ReflectionUtil

/** Functions for reducing an Observable of array pairs to smaller DataArrays
  */
case class DataStream[K: TypeTag, V: TypeTag](data: Observable[DataArray[K,V]])
    extends DataStreamPeriodReduction[K,V] {

  implicit lazy val keyClassTag = ReflectionUtil.classTag[K](typeTag[K])
  implicit lazy val valueClassTag = ReflectionUtil.classTag[V](typeTag[V])
  def keyTypeTag = typeTag[K]
  def valueTypeTag = typeTag[V]

  /** Reduce the entire stream of key,value pairs to a single pair. The returned value
    * is produced by a reduction function over the values and is wrapped in a Some
    * for consistency with other reductions. The key of the returned pair
    * is the optionally provided reduceKey, or the first key of original stream if
    * no reduceKey is provided.
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
          key -> optValue
        }
      }

    // combine the reduced block values into a single value
    val reducedTuple =
      reduceSafe(reducedBlocks){ (totalPair, nextPair) =>
        val (oldKey, optOldValue) = totalPair
        val (newKey, optNewValue) = nextPair

        oldKey -> reduceOption(optOldValue, optNewValue, reduction)
      }
    
    val reducedArray = reducedTuple.map { case (key, total) => DataArray.single(key, total) }
    DataStream(reducedArray)
  }

  /** support functions for ToPartsState */
  private object ToPartsState {
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

  /** interim state for reducing by element count */
  private case class ToPartsState(resultKeys: Vector[K],
                                  resultValues: Vector[V],
                                  currentCount: Int = 0,
                                  currentKey: Option[K] = None,
                                  currentTotal: Option[V] = None) {

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
      ( count: Int, reduction: Reduction[V] )
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

  /** apply a reduction function to a time-window of array blocks */
  def tumblingReduce[S] // format: OFF
      ( bufferOngoing: FiniteDuration,
        initialState: Future[Option[S]] = Future.successful(None) )
      ( reduceFn: (DataStream[K, V], Option[S]) => ReductionResult[K,V,S] )
      ( implicit executionContext: ExecutionContext)
      : DataStream[K, Option[V]] = { // format: ON

    val reductionResults:Observable[Future[Option[ReductionResult[K,V,S]]]] = {
      val windows = data.tumbling(bufferOngoing)

      val initialPrevious:Future[Option[ReductionResult[K,V,S]]] =
        initialState.map { oldState =>
          val oldResult = ReductionResult.stateOnly[K, V, S](oldState)
          Some(oldResult)
        }

      windows.scan(initialPrevious){ (futurePrevious, window) =>

        val previousState:Future[Option[S]] =
          futurePrevious.flatMap { optPrevious =>
            optPrevious match {
              case Some(reductionResult) => reductionResult.finishState
              case None                  => Future.successful(None)
            }
          }

        val futureOptResult: Future[Option[ReductionResult[K,V,S]]] =
          previousState.map { optPrevState =>
            val result = reduceFn(DataStream(window), optPrevState)
            Some(result)
          }

        futureOptResult
      }
    }

    val reducedStream =
      for {
        futureOptResults <- reductionResults
        optResults <- Observable.from(futureOptResults)
        if optResults.isDefined
        results = optResults.get
        arrays <- results.reducedStream.data.toVector
        if arrays.nonEmpty
      } yield {
        // combine so we get one array from each time window
        val combined = arrays.reduceLeftOption{ (a,b) => a ++ b }
        combined.getOrElse(DataArray.empty[K, Option[V]])
      }

    new DataStream(reducedStream)
  }

}

object DataStream {
  def empty[K:TypeTag, V:TypeTag] = DataStream[K,V](Observable.empty)
}

