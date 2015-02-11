package nest.sparkle.datastream

import spire.implicits._
import spire.math._


/** a way to combine two key-value pairs. The function must be associative.
  * (i.e. a semigroup binary operation for pairs)
  */
trait Reduction[V] {
  def plus(aggregateValue: V, newValue: V): V
}

/** combine key value pairs by adding them together */
case class ReduceSum[V: Numeric]() extends Reduction[V] {
  override def plus(aggregateValue: V, newValue: V): V = {
    aggregateValue + newValue
  }
}

/** combine key value pairs by taking the min value */
case class ReduceMin[V: Numeric]() extends Reduction[V] {
  override def plus(aggregateValue: V, newValue: V): V = {
    min(aggregateValue, newValue)
  }
}

/** combine key value pairs by taking the max value */
case class ReduceMax[V: Numeric]() extends Reduction[V] {
  override def plus(aggregateValue: V, newValue: V): V = {
    max(aggregateValue, newValue)
  }
}

