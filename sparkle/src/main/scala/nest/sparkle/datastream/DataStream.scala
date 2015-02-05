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

}