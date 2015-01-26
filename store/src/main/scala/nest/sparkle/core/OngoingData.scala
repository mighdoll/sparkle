package nest.sparkle.core

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import rx.lang.scala.Observable

import nest.sparkle.store.{Event, OngoingEvents}
import nest.sparkle.datastream.DataArray

/** a stream of data that may be ongoing indefinitely. The initially available data is delivered
  * asynchronously (e.g. as database ResultSets become available), and the initial observable completes
  * when all initial data is reported. The ongoing observable returns data available subsequent to the initial
  * request and typically never completes.
  */
case class OngoingData[K: TypeTag, V: TypeTag](initial: Observable[DataArray[K, V]], ongoing: Observable[DataArray[K, V]]) {
  def keyType = implicitly[TypeTag[K]]
  def valueType = implicitly[TypeTag[V]]
}
