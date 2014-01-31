package nest.sparkle.store.ram

import nest.sparkle.store.Column
import scala.collection.immutable.VectorBuilder
import scala.reflect.runtime.universe._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import rx.lang.scala.Observable
import nest.sparkle.store.Event
import scala.collection.mutable.ArrayBuffer
import nest.sparkle.store.cassandra.WriteableColumn

abstract class RamColumn[T: TypeTag: Ordering, U: TypeTag](val name: String) extends Column[T, U] {
  def keys: Seq[T]
  def values: Seq[U]
  def keyType = typeTag[T]
  def valueType = typeTag[U]
  def exists(implicit context: ExecutionContext): Future[Unit] = Future.successful()

  def readBefore(start: T, maxResults: Long = Long.MaxValue) // format: OFF
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    ???
  }

  def readAfter(start: T, maxResults: Long = Long.MaxValue) // format: OFF
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    ???
  }

  def readRange(start: Option[T] = None, end: Option[T] = None) // format: OFF
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    val (startDex, endDex) = keyRange(start, end)
    val results = keys.slice(startDex, endDex) zip values.slice(startDex, endDex)
    val events = results.map { case (key, value) => Event(key, value) }
    val result = Observable.from(events)

    result
  }

  /** Return the indices in the time array for the specified time bounds.
    * indices are inclusive of the first element index, and exclusive of the last index.
    * If the last index is 0, no elements are available (no index is before 0).
    */
  private def keyRange(start: Option[T], end: Option[T]): (Int, Int) = {
    val ordering = Ordering[T]
    val startDex: Int = {
      start map { startValue =>
        keys.indexWhere(ordering.gteq(_, startValue))
      } getOrElse 0
    }
    val afterEnd: Int = {
      end map { endValue =>
        keys.length - keys.reverseIterator.indexWhere(ordering.lteq(_, endValue))
      } getOrElse keys.length
    }
    (startDex, afterEnd)
  }
}

object WriteableRamColumn {
  def apply[T: TypeTag: Ordering, U: TypeTag](name: String): WriteableRamColumn[T, U] =
    new WriteableRamColumn(name)
}

class WriteableRamColumn[T: TypeTag: Ordering, U: TypeTag](name: String)
    extends RamColumn[T, U](name) with WriteableColumn[T, U] {
  val keys = ArrayBuffer[T]()
  val values = ArrayBuffer[U]()
  
  def write(events: Iterable[Event[T, U]]) // format: OFF
      (implicit executionContext: ExecutionContext): Future[Unit] = { // format: ON
    for {
      Event(key, value) <- events
    } {
      keys.append(key)
      values.append(value)
    }
    Future.successful()
  }
  
  def create(description: String)(implicit executionContext: ExecutionContext): Future[Unit] = {
    Future.successful()
  }
  
  def erase()(implicit executionContext:ExecutionContext): Future[Unit] = {
    keys.clear()
    values.clear()
    Future.successful()
  }
// SCALA TypedActor for this?
}
