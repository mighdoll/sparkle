package nest.sparkle.core

import scala.reflect.{ClassTag, classTag}
import scala.{specialized => spec}

object DataArray {
  /** return an DataArray with a single key,value pair */
  def single[K: ClassTag, V: ClassTag](key: K, value: V): DataArray[K, V] = {
    DataArray(Array(key), Array(value))
  }
}

/** A pair of arrays, suitable for efficiently storing a key,value collection
  * Both arrays must be contain the same number of elements.
  */
// LATER extend Traversable[Tuple2[K,V]] / CBF to get all the collection operators
// (and then implement specialized variants for only a few)
// LATER use HList instead of two arrays
case class DataArray[K: ClassTag, V: ClassTag](keys: Array[K], values: Array[V]) {
  self =>

  require(keys.length == values.length)
  
  // TODO: prevent keys & value elements from being mutable
  // TODO add more high level functions
  
  def length: Int = keys.length
  
  override def equals(other: Any): Boolean = {
    other match {
      case that: DataArray[K,V] =>
        // TODO: rewrite to eliminate boxing/unboxing
        this.length == that.length &&
        (0 until length).forall { i =>
          this.keys(i) == that.keys(i) && this.values(i) == that.values(i)
        }
      case _                    => false
    }
  }
  
  // TODO: something smarter
  override def hashCode: Int = {
    41 * (41 + keys(0).hashCode()) + values(0).hashCode()
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

  def iterator: Iterator[(K, V)] = { // TODO specialize
    new Iterator[(K, V)]() {
      var index = 0
      override def hasNext(): Boolean = index < self.length
      override def next(): (K, V) = {
        var result = (keys(index), values(index))
        index = index + 1
        result
      }
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

  /** return a new DataArray with all of the elements in this DataArray except the first pair */
  def tail: DataArray[K, V] = { // TODO specialize
    // for now, we simply copy. (Fancier would be a view that peeks into the underlying array)
    val newKeys = keys.tail.toArray
    val newValues = values.tail.toArray
    DataArray(newKeys, newValues)
  }

  /** Optionally return the first pair in this DataArray */
  def headOption: Option[(K, V)] = {
    if (keys.length > 0) {
      Some((keys(0), values(0)))
    } else None
  }

  /** Optionally return the DataArray values combined into a single value by a provided
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

  def isEmpty: Boolean = keys.length == 0
  
}
