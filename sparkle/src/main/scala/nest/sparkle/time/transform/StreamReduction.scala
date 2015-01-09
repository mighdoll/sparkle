package nest.sparkle.time.transform
import scala.language.higherKinds
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.collection.mutable.ArrayBuffer
import nest.sparkle.core.DataArray
import scala.reflect.runtime.universe._
import spire.implicits._
import spire.math.Numeric
import nest.sparkle.util.PeriodWithZone
import rx.lang.scala.Subject
import nest.sparkle.util.Log
import rx.lang.scala.Observable
import org.joda.time.{ Interval => JodaInterval }
import nest.sparkle.util.RecoverNumeric
import scala.util.Failure
import scala.util.Success
import nest.sparkle.util.PeekableIterator
import rx.lang.scala.Notification
import scala.concurrent.duration._
import nest.sparkle.time.transform.DataArraysReduction._


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

