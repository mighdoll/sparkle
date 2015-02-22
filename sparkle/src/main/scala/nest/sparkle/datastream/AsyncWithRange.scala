package nest.sparkle.datastream


import scala.reflect.runtime.universe._
import scala.concurrent.{Future, ExecutionContext}
import rx.lang.scala.Observable
import nest.sparkle.util.ReflectionUtil
import nest.sparkle.datastream.DataStream.flattenFutureStream

/** A TwoPartStream containing data in the form it comes off from the database. Initial request data
  * is asynchronously delivered, block at a time, as it returned from the database driver.
  * The TwoPartStream bundles the range interval request that produced this data for further
  * downstream processing.
  */
class AsyncWithRange[K: TypeTag, V: TypeTag] // format: OFF
    ( val initial: DataStream[K,V],
      val ongoing: DataStream[K,V],
      val requestRange: Option[SoftInterval[K]] )
    extends TwoPartStream[K,V, AsyncWithRange]
      with RequestRange[K] with AsyncReduction[K,V] { // format: ON

  def mapData[B: TypeTag] // format: OFF
      ( fn: DataArray[K,V] => DataArray[K,B] )
      ( implicit execution:ExecutionContext )
      : AsyncWithRange[K,B] = { // format: ON
    val newInitial = DataStream(initial.data.map(fn))
    val newOngoing = DataStream(ongoing.data.map(fn))
    new AsyncWithRange(newInitial, newOngoing, requestRange)
  }

  override def keyType = typeTag[K]
  override def valueType = typeTag[V]

  implicit lazy val keyClassTag = ReflectionUtil.classTag[K](keyType)
  implicit lazy val valueClassTag = ReflectionUtil.classTag[V](valueType)

  override def mapInitial[A](fn: DataArray[K, V] => A): Observable[A] = initial.data map fn
  override def mapOngoing[A](fn: DataArray[K, V] => A): Observable[A] = ongoing.data map fn

  override def doOnEach(fn: DataArray[K, V] => Unit): AsyncWithRange[K, V] = {
    new AsyncWithRange(
      initial = DataStream(initial.data doOnEach fn),
      ongoing = DataStream(ongoing.data doOnEach fn),
      requestRange = requestRange
    )
  }

  override def plus(other: TwoPartStream[K, V, AsyncWithRange]) // format: OFF
    : TwoPartStream[K, V, AsyncWithRange] = { // format: ON
    new AsyncWithRange(
      initial = DataStream(initial.data ++ other.self.initial.data),
      ongoing = DataStream(ongoing.data ++ other.self.ongoing.data),
      requestRange = requestRange
    )
  }
  
}

object AsyncWithRange {

  /** an AsyncWithRange that simply returns an error over both its initial and ongoing streams */
  def error[K: TypeTag, V: TypeTag] // format: OFF
      ( err: Throwable, requestRange: Option[SoftInterval[K]] = None)
      : AsyncWithRange[K, V] = { // format: ON
    new AsyncWithRange[K, V](
        initial = DataStream[K,V](Observable.error(err)), 
        ongoing = DataStream[K,V](Observable.error(err)),
        requestRange = requestRange)
  }

  /** return an AsyncWithRange from a Future[AsyncWithRange] */
  def flattenFutureAsyncWithRange[K:TypeTag, V:TypeTag]
      ( futureStream: Future[AsyncWithRange[K, V]],
        range:Option[SoftInterval[K]] )
      ( implicit executionContext: ExecutionContext)
      : AsyncWithRange[K,V] = {

    val initial = flattenFutureStream(futureStream.map(_.initial))
    val ongoing = flattenFutureStream(futureStream.map(_.ongoing))

    new AsyncWithRange(
      initial = initial,
      ongoing = ongoing,
      requestRange = range
    )
  }

}
