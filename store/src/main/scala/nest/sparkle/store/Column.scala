package nest.sparkle.store

import scala.concurrent.{Future, ExecutionContext}
import scala.reflect.runtime.universe._
import rx.lang.scala.Observable

import nest.sparkle.core.OngoingData
import nest.sparkle.measure.{Span, DummySpan}

case class OngoingEvents[T, U](initial: Observable[Event[T, U]], ongoing: Observable[Event[T, U]])

/** a readable column of data that supports simple range queries.  */
trait Column[T, U]
{
  /** name of this column */
  def name: String

  def keyType: TypeTag[_]

  def valueType: TypeTag[_]

  /** Obsolete, use ReadRange. */
  def readRangeOld   // format: OFF
      ( start: Option[T] = None,
        end: Option[T] = None,
        limit: Option[Long] = None,
        parentSpan: Option[Span] = None )
      ( implicit execution: ExecutionContext): OngoingEvents[T, U] // format: ON
  
  /** read a slice of events from the column, inclusive of the start and ends.
    * If start is missing, read from the first element in the column.  If end is missing
    * read from the last element in the column.  */
  def readRange // format: OFF
      ( start: Option[T] = None,
        end: Option[T] = None,
        limit: Option[Long] = None,
        parentSpan: Option[Span] = None )
      ( implicit execution: ExecutionContext): OngoingData[T, U] // format: ON


  /** optionally return the last key in the column */
  def lastKey()(implicit execution: ExecutionContext, parentSpan: Span): Future[Option[T]] = ???

  /** optionally return the first key in the column */
  def firstKey()(implicit execution: ExecutionContext, parentSpan: Span): Future[Option[T]] = ???

  /** return a count of the items in the column, or zero if it is empty */
  def countItems(start: Option[T] = None, end: Option[T] = None)
      ( implicit execution: ExecutionContext, parentSpan: Span = DummySpan): Future[Long] = ???

  // LATER add authorization hook, to validate permission to read a range
}

// LATER consider making value more flexible:
// . a sequence?  values:Seq[V].  e.g. if we want to store:  [1:[123, 134], 2:[100]]
// . an hlist?  e.g. if we want to store a csv file with multiple separately typed columns per row
// . possibly a typeclass that covers all single, sequence, and typed list cases?
/** an single item in the datastore, e.g. a timestamp and value */
// Obsolete, DataArrays are the new black.
case class Event[T, V](argument: T, value: V)

// TODO get rid of Event in favor of DataArray
object Event {
  type Events[T, U] = Seq[Event[T, U]]
}
