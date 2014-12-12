package nest.sparkle.core

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import rx.lang.scala.Observable

import nest.sparkle.store.{Event, OngoingEvents}


/** a stream of data that may be ongoing indefinitely. The initially available data is delivered
  * asynchronously (e.g. as database ResultSets become available), and the initial observable completes
  * when all initial data is reported. The ongoing observable returns data available subsequent to the initial
  * request and typically never completes.
  */
case class OngoingData[K: TypeTag, V: TypeTag](initial: Observable[ArrayPair[K, V]], ongoing: Observable[ArrayPair[K, V]]) {
  def keyType = implicitly[TypeTag[K]]
  def valueType = implicitly[TypeTag[V]]
}

/** temporary conversion shim, until we produce ArrayPairs from the storage layer */
object OngoingDataShim {

  /** (temporary shim) conversts OngoingEvents to OngoingData */
  def fromOngoingEvents[K: TypeTag, V: TypeTag](ongoingEvents: OngoingEvents[K, V]): OngoingData[K, V] = {

    val initial = eventsToArrayPairs(ongoingEvents.initial)
    val ongoing = eventsToArrayPairs(ongoingEvents.ongoing)
    OngoingData(initial, ongoing)
  }

  /** Convert an observable of events to an observable of array pairs. We buffer the data by 5 milliseconds to
    * try and collect a worthwhile amount of data to stuff into an array. (this is intended as temporary code only
    * until we update the store to generate ArrayPairs
    */
  private def eventsToArrayPairs[K: TypeTag, V: TypeTag](events: Observable[Event[K, V]]): Observable[ArrayPair[K, V]] = {
    val keyType = implicitly[TypeTag[K]]
    implicit val keyClass = ClassTag[K](keyType.mirror.runtimeClass(keyType.tpe))

    val valueType = implicitly[TypeTag[V]]
    implicit val valueClass = ClassTag[V](valueType.mirror.runtimeClass(valueType.tpe))

    for {
      obserableBuffer <- events.tumbling(5.milliseconds)
      eventBuffer <- obserableBuffer.toVector
      if (eventBuffer.nonEmpty)
      keys = eventBuffer.map { case Event(k, v) => k }
      values = eventBuffer.map { case Event(k, v) => v }
    } yield {
      ArrayPair(keys.toArray, values.toArray)
    }
  }
}