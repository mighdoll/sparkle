package nest.sparkle.time.transform

import scala.language.higherKinds

import spire.implicits._
import spire.math.Numeric


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

