package nest.sparkle.datastream

import scala.collection.IndexedSeqLike
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder
import scala.reflect.ClassTag

object DataArray {
  /** return an DataArray with a single key,value pair */
  def single[K: ClassTag, V: ClassTag](key: K, value: V): DataArray[K, V] = {
    DataArray(Array(key), Array(value))
  }

  // unneeded once we extend Traversable?
  def fromPairs[K: ClassTag, V: ClassTag](pairs: Iterable[(K, V)]): DataArray[K, V] = {
    val keys = pairs.map { case (k, v) => k }.toArray
    val values = pairs.map { case (k, v) => v }.toArray
    DataArray(keys, values)
  }

  def newBuilder[K: ClassTag, V: ClassTag] = new DataArrayBuilder[K,V]
 
  implicit def canBuildFrom[K: ClassTag, V: ClassTag]
      : CanBuildFrom[DataArray[K, V], (K, V), DataArray[K, V]] =

    new CanBuildFrom[DataArray[K, V], (K, V), DataArray[K, V]] {
      override def apply(): Builder[(K, V), DataArray[K, V]] = newBuilder
      override def apply(from: DataArray[K, V]): Builder[(K, V), DataArray[K, V]] = newBuilder
    }

  def empty[K:ClassTag, V:ClassTag] = DataArray(Array[K](), Array[V]())
}

/**
 * A pair of arrays, suitable for efficiently storing a key,value collection
 * Both arrays must be contain the same number of elements.
 */
// LATER use HList instead of two arrays
// LATER use TypeTags, so we don't need to keep converting to class tags
case class DataArray[K: ClassTag, V: ClassTag](keys: Array[K], values: Array[V])
    extends IndexedSeq[(K, V)] with IndexedSeqLike[(K, V), DataArray[K, V]] {
  self =>

  require(keys.length == values.length)

  // TODO: prevent keys & value elements from being mutable

  override def length: Int = keys.length

  override def apply(index: Int): (K, V) = (keys(index), values(index))

  override protected[this] def newBuilder: Builder[(K, V), DataArray[K, V]] = {
    DataArray.newBuilder
  }

  private val debugPrintSize = 5
  override def toString = {
    if (isEmpty) {
      "()"
    } else {
      val resultsString = keys.take(debugPrintSize).zip(values).map{ case (k, v) => s"($k,$v)" }.mkString (", ")
      if (length > debugPrintSize) {
        resultsString + ", ..."
      } else {
        resultsString
      }
    }
  }

  /** apply a function to each key,value pair in the DataArray. */
  // note that a standard foreach would take a tuple2 parameter, so we don't call this foreach
  def foreachPair(fn: (K, V) => Unit): Unit = { // TODO specialize
    var index = 0
    while (index < keys.length) {
      fn(keys(index), values(index))
      index += 1
    }
  }

  /** apply a mapping function and return the result as an array */
  def mapToArray[A: ClassTag](fn: (K, V) => A): Array[A] = { // TODO specialize
    val array = new Array[A](length)
    var index = 0
    while (index < keys.length) {
      array(index) = fn(keys(index), values(index))
      index += 1
    }
    array
  }


  /**
   * Optionally return the DataArray values combined into a single value by a provided
   * binary operation function. The provided function should be associative and commutative.
   */
  def valuesReduceLeftOption(binOp: (V, V) => V): Option[V] = {
    def reduceN(): V = {
      var aggregateValue = headOption.map { case (k, v) => v }.get
      tail.foreachPair { (key, value) =>
        aggregateValue = binOp(aggregateValue, value)
      }
      aggregateValue
    }

    length match {
      case 0 => None
      case 1 => headOption map { case (k, v) => v }
      case n => Some(reduceN())
    }
  }

}

