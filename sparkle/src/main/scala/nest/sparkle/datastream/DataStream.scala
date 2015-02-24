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
    extends DataStreamPeriodReduction[K,V] with DataStreamCountReduction[K,V] {

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
      ( reduction: IncrementalReduction[V], reduceKey:Option[K] = None)
      ( implicit parentSpan:Span )
      : DataStream[K, Option[V]] = {

    var firstKey:Option[K] = None

    // process all elements as a side effect, result completes when the stream is done
    val allDone:Observable[Option[Unit]] =
      data.filter(!_.isEmpty).map { pairs =>
        Span("reduceBlock").time {
          pairs.values.foreach { value => reduction.accumulate(value)}
          if (firstKey.isEmpty) {
            firstKey = pairs.keys.headOption
          }
        }
      }.lastOption

    val reducedResult:Observable[DataArray[K,Option[V]]] = allDone.flatMap { _ =>
      if (reduction.currentTotal.isDefined) {
        val key = reduceKey.orElse(firstKey).get
        val value = reduction.currentTotal
        Observable.from(Seq(DataArray.single(key, value)))
      } else {
        Observable.empty
      }
    }

    DataStream(reducedResult)
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

  /** Unwrap a Future context around a stream, by combining the asynchronous future
    * with the asynchronous observable within the DataStream. return the DataStream
    * not wrapped in a future. */
  def flattenFutureStream[K:TypeTag,V:TypeTag](futureStream: Future[DataStream[K,V]])
      ( implicit executionContext: ExecutionContext)
      : DataStream[K,V] = {

    val obsStream = Observable.from(futureStream)
    val obsDataArray = obsStream.flatMap(_.data) // flatMap is the key move
    DataStream(obsDataArray)
  }

}

