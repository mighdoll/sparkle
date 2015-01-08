package nest.sparkle.time.transform


import scala.reflect.runtime.universe._
import nest.sparkle.time.protocol.RangeInterval
import scala.concurrent.ExecutionContext
import rx.lang.scala.Observable
import scala.concurrent.Future
import nest.sparkle.core.OngoingData
import nest.sparkle.core.ArrayPair
import scala.{ specialized => spec }
import nest.sparkle.util.ReflectionUtil

/** A DataStream containing data in the form it comes off from the database. Initial request data
  * is asynchronously delivered, block at a time, as it returned from the database driver.
  * The DataStream bundles the range interval request that produced this data for further
  * downstream processing.
  */
case class AsyncWithRange[K: TypeTag, V: TypeTag] // format: OFF
    (initial: Observable[ArrayPair[K,V]], 
     ongoing: Observable[ArrayPair[K,V]],
     requestRange: Option[RangeInterval[K]]) 
     extends DataStream[K,V,AsyncWithRange] with RequestRange[K]
    { // format: ON

  def mapData[B: TypeTag] // format: OFF
      (fn: ArrayPair[K,V] => ArrayPair[K,B])
      (implicit execution:ExecutionContext)
      : AsyncWithRange[K,B] = { // format: ON
    val newInitial = initial.map(fn)
    val newOngoing = ongoing.map(fn)
    AsyncWithRange(newInitial, newOngoing, requestRange)
  }

  override def keyType = typeTag[K]
  override def valueType = typeTag[V]

  implicit lazy val keyClassTag = ReflectionUtil.classTag[K](keyType)
  implicit lazy val valueClassTag = ReflectionUtil.classTag[V](valueType)

  override def mapInitial[A](fn: ArrayPair[K, V] => A): Observable[A] = initial map fn
  override def mapOngoing[A](fn: ArrayPair[K, V] => A): Observable[A] = ongoing map fn

  override def doOnEach(fn: ArrayPair[K, V] => Unit): AsyncWithRange[K, V] = {
    copy(
      initial = initial doOnEach fn,
      ongoing = ongoing doOnEach fn
    )
  }

  override def plus(other: DataStream[K, V, AsyncWithRange]) // format: OFF
    : DataStream[K, V, AsyncWithRange] = { // format: ON
    AsyncWithRange(
      initial = initial ++ other.self.initial,
      ongoing = ongoing ++ other.self.ongoing,
      requestRange = requestRange
    )
  }

}

object AsyncWithRange {
  /** convenience constructor for creating an instance from an OngoingData */
  def apply[K, V] // format: OFF
    (ongoingData: OngoingData[K, V], requestRange: Option[RangeInterval[K]])
    : AsyncWithRange[K, V] = { // format: ON
    (new AsyncWithRange(ongoingData.initial, ongoingData.ongoing, requestRange)(ongoingData.keyType, ongoingData.valueType))
  }

  def error[K: TypeTag, V: TypeTag](err: Throwable, requestRange: Option[RangeInterval[K]]): AsyncWithRange[K, V] = {
    AsyncWithRange[K, V](Observable.error(err), Observable.error(err), requestRange)
  }
}
