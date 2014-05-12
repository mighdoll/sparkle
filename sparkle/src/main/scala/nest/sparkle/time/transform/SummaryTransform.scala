/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.time.transform

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe.typeTag
import scala.util.{ Failure, Success }
import spray.json._
import nest.sparkle.store.{ Column, Event }
import nest.sparkle.time.protocol.{ JsonDataStream, JsonEventWriter, KeyValueType, RangeParameters }
import nest.sparkle.util.{ RecoverJsonFormat, RecoverNumeric, RecoverOrdering }
import nest.sparkle.util.ObservableFuture.WrappedObservable
import rx.lang.scala.Observable
import scala.reflect.runtime.universe._
import spire.math.{ Numeric, max }
import spire.implicits._
import spire.algebra.Order
import nest.sparkle.util.RecoverFractional

object SummaryTransform {
  /** a handy alias for a block of events */
  type Events[T, U] = Seq[Event[T, U]]  // TODO move to another package
}

/** summarize a block of event data */
protected abstract class PartitionSummarizer[T: TypeTag, U: TypeTag] {
  def summarizePart(data: Seq[Event[T, U]]): Seq[Event[T, U]]
}

/** holds typeclasses based on the runtime type of the column */
protected case class ColumnMetadata[T: TypeTag, U: TypeTag]() {

  /** order events by the order of their values */
  lazy val valueOrderEvents = new Ordering[Event[T, U]] {
    implicit val valueOrdering = RecoverOrdering.ordering[U](typeTag[U])
    def compare(x: Event[T, U], y: Event[T, U]): Int = {
      valueOrdering.compare(x.value, y.value)
    }
  }

  lazy val optNumericKey = RecoverNumeric.optNumeric[T](typeTag[T])
  lazy val optNumericValue = RecoverNumeric.optNumeric[U](typeTag[U])
  def keyType = typeTag[T]
  def valueType = typeTag[U]
}

/** We ultimately need an object that will support the ColumnTransform interface.
  * The calling code will a selected column (derived from the StreamRequest's source selector),
  * a json data stream for sending back to the client.
  *
  * We need to create a bunch of these SummaryTransform objects, one for
  * each type of summary (min, max, etc.).
  *
  * Furthermore, each SummaryTransform will need to dynamically adapt at runtime to the
  * type of parameters in the columns based on the type requirements of the transform
  * itself. For example a Long,String column does not support the SummaryMax
  * transform.
  *
  * We recover the necessary type information to do this validation in two stages.
  * In the first stage, we recover typetag and json format typeclasses based on the
  * typetag in the column (the type of each column's data is stored persistently with
  * the column).
  *
  * In the second stage, each individual transform recovers the
  * typeclasses that are needed for that particular transform, (again using the
  * typeTags deserialized from the column's metadata). For example, typical
  * transforms might expect that keys or values are Numeric, or at least Ordered.
  */
/**
  */
trait SummaryTransform extends ColumnTransform {
  protected def createPartitionSummarizer[T: TypeTag, U: TypeTag](): PartitionSummarizer[T, U]

  // TODO make column types independent of output format 
  // e.g. column of double values but we write long counts

  /** transform column data and produce a stream of json-encoded data */
  override def apply[T, U]( // format: OFF  
       column: Column[T, U],
       transformParameters: JsObject
     ) (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    implicit val keyFormat = RecoverJsonFormat.jsonFormat[T](column.keyType)
    implicit val valueFormat = RecoverJsonFormat.jsonFormat[U](column.valueType)
    implicit val keyType = column.keyType.asInstanceOf[TypeTag[T]]
    implicit val valueType = column.valueType.asInstanceOf[TypeTag[U]]

    val summarizePartition = createPartitionSummarizer[T, U]()

    // TODO handle edgeExtra

    Transform.rangeParameters(transformParameters)(keyFormat) match {
      case Success(range) =>
        val summaryEvents = summarizeColumn(column, summarizePartition, range)
        JsonDataStream(
          dataStream = JsonEventWriter.fromObservableSeq(summaryEvents),
          streamType = KeyValueType
        )
      case Failure(err) => JsonDataStream.error(err)
    }
  }

  /** read a range of column data, divide it into blocks, returning an observable
    * with each block replaced with a summarized value
    */
  def summarizeColumn[T, U](column: Column[T, U], summarizePartition: PartitionSummarizer[T, U],
    range: RangeParameters[T])(implicit execution: ExecutionContext): Observable[Seq[Event[T, U]]] = {
    val eventStream = column.readRange(range.start, range.end)
    val summarizedEvents = eventStream.toFutureSeq.map { events =>
      partitionEvents(events, range, column.keyType).flatMap { part =>
        if (part.nonEmpty) {
          summarizePartition.summarizePart(part)
        } else { Seq() }
      }
    }
    Observable.from(summarizedEvents)
  }

  /** Partition the incoming events based on a division of the key range (e.g. time)
    * so that we can summarize each partition individually.
    */
  private def partitionEvents[T, U](events: Seq[Event[T, U]], params: RangeParameters[T],
    keyType: TypeTag[_]): Seq[Seq[Event[T, U]]] = {
    if (events.isEmpty) {
      Seq()
    } else {
      RecoverNumeric.optNumeric[T](keyType) map { implicit numeric =>
        partitionIterator(events, params)(keyType.asInstanceOf[TypeTag[T]], numeric).toSeq
      } getOrElse {
        partitionEventsByCount(events, params.maxResults)
      }
    }
  }

  /** partition the incoming events based on a count of the events so that we can
    * summarize each partition individually
    */
  private def partitionEventsByCount[T, U](events: Seq[Event[T, U]], maxPartitions: Int): Seq[Seq[Event[T, U]]] = {
    val partitionCount =
      if (events.length < maxPartitions) { 1 } else { events.length / maxPartitions }
    events.grouped(partitionCount).toSeq
  }

  /** For events with numeric keys (e.g. time), we want to partition equally based on the
    * requested range.
    *
    * If the start or end of the range is not fully specified in the request,
    * returns partitions based on the range of the keys in the event data.
    *
    * When using the end value from the data, note a subtle difference between partitioning
    * based on the end value in the data and the end value specified in the requested range.
    * Requested ranges are exclusive of the end value, but end values discovered from
    * the data are inclusive of the end value.
    */
  private def partitionIterator[T: TypeTag: Numeric, U](events: Seq[Event[T, U]], params: RangeParameters[T])// format: OFF
      :Iterator[Seq[Event[T,U]]] =  { // format: ON
    val start = params.start.getOrElse { events.head.argument }
    val (end, includeEnd) = params.end match {
      case Some(explicitEnd) => (explicitEnd, false)
      case None              => (events.last.argument, true)
    }
    val range = end - start
    val parts = params.maxResults
    val step = stepSize(range, parts)

    /** portion of the event sequence not yet consumed by the iterator */
    var remaining = events

    /** end of the next partition to be consumed */
    var partEnd = start + step

    // return an iterator that walks through the event sequence, one partition at a time
    new Iterator[Seq[Event[T, U]]] {
      override def hasNext(): Boolean = !remaining.isEmpty
      override def next(): Seq[Event[T, U]] = {
        val (inPartition, remainder) = {
          if (partEnd >= end && includeEnd) {
            remaining.span { event => event.argument <= partEnd }
          } else {
            remaining.span { event => event.argument < partEnd }
          }
        }
        remaining = remainder
        partEnd = partEnd + step
        inPartition
      }
    }
  }

  /** return the partition step size, rounding appropriately to a minimum partition size of 1 for
    * integral domain types
    */
  private def stepSize[T: Numeric: TypeTag](range: T, parts: Int): T = {
    val numeric = implicitly(Numeric[T])
    val fractionalOpt = RecoverFractional.optFractional[T](typeTag[T])
    val step: T =
      fractionalOpt match {
        case Some(fractional) => range / parts
        case None             => max(numeric.fromInt(1), range / parts)
      }
    step
  }

}

