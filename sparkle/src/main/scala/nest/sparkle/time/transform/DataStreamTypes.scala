package nest.sparkle.time.transform

import scala.reflect.runtime.universe._
import nest.sparkle.time.protocol.RangeInterval
import scala.concurrent.ExecutionContext
import rx.lang.scala.Observable
import scala.concurrent.Future
import nest.sparkle.core.OngoingData
import nest.sparkle.core.ArrayPair
import scala.{specialized => spec}

/** A DataStream containing data in the form it comes off from the database. Initial request data
  * is asynchronously delivered, block at a time, as it returned from the database driver.
  * The DataStream bundles the range interval request that produced this data for further downstream
  * processing.
  */
case class AsyncWithRequestRange[K: TypeTag, V: TypeTag] // format: OFF
    (initial: Observable[ArrayPair[K,V]], 
     ongoing: Observable[ArrayPair[K,V]],
     fromRange: Option[RangeInterval[K]])
    { // format: ON

  def mapData[A:TypeTag, B: TypeTag] // format: OFF
      (fn: ArrayPair[K,V] => ArrayPair[K,B])
      (implicit execution:ExecutionContext)
      : AsyncWithRequestRange[K,B] = { // format: ON
    val newInitial = initial.map(fn)
    val newOngoing = ongoing.map(fn)
    new AsyncWithRequestRange(newInitial, newOngoing, fromRange)
  }
  
  def keyType = typeTag[K]
  def valueType = typeTag[V]
}

object AsyncWithRequestRange {
  /** convenience constructor for creating an instance from an OngoingData */
  def apply[K, V] // format: ON
      (ongoingData: OngoingData[K, V], fromRange: Option[RangeInterval[K]])
      : AsyncWithRequestRange[K, V] = // format: OFF
    new AsyncWithRequestRange(ongoingData.initial, ongoingData.ongoing, fromRange)(ongoingData.keyType, ongoingData.valueType)
}

/** A DataStream that buffers all initially requested data into a single array.
  * The DataStream also bundles the range interval request that produced this data for further downstream
  * processing.
  */
case class BufferedWithRequestRange[K, V] // format: OFF
    (initial: Future[ArrayPair[K,V]], 
     ongoing: Observable[ArrayPair[K,V]],
     fromRange: Option[RangeInterval[K]])
    { // format: ON

  def mapData[A:TypeTag, B: TypeTag] // format: OFF
      (fn: ArrayPair[K,V] => ArrayPair[K,B])
      (implicit execution:ExecutionContext)
      : BufferedWithRequestRange[K,B] = { // format: ON
    val newInitial = initial.map(fn)
    val newOngoing = ongoing.map(fn)
    new BufferedWithRequestRange(newInitial, newOngoing, fromRange)
  }
}

/** typeclass proxy implementations for each of our standard DataStream types */
object DataStreamType {
  // SCALA - can these be DRY'd somehow? 

  implicit object AsyncWithRequestRangeProxy extends DataStream[AsyncWithRequestRange] {
    override def mapData[K, V, B: TypeTag] // format: OFF
        (as: AsyncWithRequestRange[K,V])
        (fn: ArrayPair[K,V] => ArrayPair[K,B])
        (implicit execution:ExecutionContext)
        : AsyncWithRequestRange[K, B] = { // format: ON
      as.mapData(fn)
    }
  }

  implicit object BufferedWithRequestRangeProxy extends DataStream[BufferedWithRequestRange] {
    override def mapData[K, V, B: TypeTag] // format: OFF
        (as: BufferedWithRequestRange[K,V])
        (fn: ArrayPair[K,V] => ArrayPair[K,B])
        (implicit execution:ExecutionContext)
        : BufferedWithRequestRange[K, B] = { // format: ON
      as.mapData(fn)
    }
  }

}