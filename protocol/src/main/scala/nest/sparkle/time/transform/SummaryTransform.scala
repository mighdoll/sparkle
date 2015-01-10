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
import scala.util.control.Exception._
import spray.json._
import nest.sparkle.store.{ Column, Event }
import nest.sparkle.time.protocol.{ JsonDataStream, JsonEventWriter, KeyValueType, RangeInterval, SummaryParameters }
import nest.sparkle.util.{ RecoverJsonFormat, RecoverNumeric, RecoverOrdering }
import nest.sparkle.util.ObservableFuture.WrappedObservable
import rx.lang.scala.Observable
import scala.reflect.runtime.universe._
import spire.math.{ Numeric, max }
import spire.implicits._
import spire.algebra.Order
import nest.sparkle.util.RecoverFractional
import nest.sparkle.time.protocol.SummaryParameters
import nest.sparkle.time.protocol.TransformParametersJson.SummaryParametersFormat
import scala.util.Try
import nest.sparkle.store.OngoingEvents
import nest.sparkle.util.Exceptions.NYI

object SummaryTransform {
  /** a handy alias for a block of events */
  type Events[T, U] = Seq[Event[T, U]] // TODO move to another package
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

/** A SummaryTransform is a kind of ColumnTransform that handles the details of summarizing
  * a column by running a summary function on partitions of input data. The SummaryTransform
  * is handed a column and a json object (transformParameters) describing the slice of data
  * to summarize and the number of output elements desired.
  *
  * Subclasses of SummaryTransform provide a function that summarizes the data in each partition.
  *
  * The calling code provides a Column (derived from the StreamRequest's source selector),
  * The SummaryTrnasform returns a json data stream for sending back to the client as part
  * of the Streams response.
  *
  * There are a number of subclasses of SummaryTransform objects, one for
  * each type of summary (min, max, etc.).
  *
  * Each SummaryTransform dynamically adapts at runtime to the
  * type of parameters in the columns based on the type requirements of the transform
  * itself. For example a Long,String column does not support the SummaryMax
  * transform.
  *
  * The necessary type information to do type validation is recovered
  * in two stages. In the first stage, SummaryTransform collects typetag and
  * json format typeclasses based on the typetag in the column (the type of each
  * column's data is stored persistently in the Store metadata).
  *
  * In the second stage, each individual transform recovers the
  * typeclasses that are needed for that particular transform, (again using the
  * typeTags deserialized from the column's metadata). For example, typical
  * transforms might expect that keys or values are Numeric.
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

    parseSummaryParameters(transformParameters)(keyFormat) match {
      case Success(summaryParameters) =>
        val summaryEvents = summarizeColumn(column, summarizePartition, summaryParameters)
        JsonDataStream(
          dataStream = JsonEventWriter.fromObservableSeq(summaryEvents),
          streamType = KeyValueType
        )
      case Failure(err) =>
        JsonDataStream.error(err)
    }
  }

  private def parseSummaryParameters[T: JsonFormat](transformParameters: JsObject): Try[SummaryParameters[T]] = {
    nonFatalCatch.withTry { transformParameters.convertTo[SummaryParameters[T]] }
  }

  /** divide the source range into parts, and return an observable containing a summarized event for each part
    */
  def summarizeColumn[T, U](column: Column[T, U], summarizePartition: PartitionSummarizer[T, U], // format: OFF
       params: SummaryParameters[T])(implicit execution: ExecutionContext)
       : Observable[Seq[Event[T, U]]] = { // format: ON

    val eventsIntervals = SelectRanges.fetchRanges(column, params.ranges)
    implicit val keyTag = column.keyType.asInstanceOf[TypeTag[T]]

    def numericKeyByPartCount(count: Int)(numericKey: Numeric[T]): Seq[Observable[Seq[Event[T, U]]]] = {
      eventsIntervals.map {
        case IntervalAndEvents(intervalOpt, events) =>
          summarizeOneNumericInterval(events.initial, count, intervalOpt, summarizePartition)(keyTag, numericKey, execution)
      }
    }

    val partResults =
      (RecoverNumeric.optNumeric[T](keyTag), params.partByCount, params.partBySize) match {
        case (_, Some(_), Some(_))                  => ??? // TODO return an error here, shouldn't be allowed to set both
        case (Some(numericKey), Some(count), None)  => numericKeyByPartCount(count)(numericKey)
        case (Some(numericKey), None, None)         => numericKeyByPartCount(1)(numericKey)
        case (Some(numericKey), None, Some(period)) => NYI("summarize by period string")
        case (None, Some(count), None)              => ??? //  partitionEventsByCount(events, partitions) // TODO fix me
        case (None, None, Some(period))             => ??? //  partitionEventsByCount(events, partitions) // TODO fix me
        case (None, None, None)                     => ??? // TODO 
      }

    partResults.reduce { (a, b) => a ++ b }
    // TODO summarize ongoing event data as well

  }

  /** summarize all the partitions in one specified time interval */
  def summarizeOneNumericInterval[T: TypeTag: Numeric, U](events: Observable[Event[T, U]], // format: OFF
      partitions: Int, intervalOpt:Option[RangeInterval[T]], summarizePartition: PartitionSummarizer[T, U])
      (implicit execution: ExecutionContext): Observable[Seq[Event[T, U]]] = { // format: ON
    val summarizedEvents = events.toFutureSeq.map { events =>
      partitionNumericEvents(events, partitions, intervalOpt).flatMap { part =>
        if (part.nonEmpty) {
          // TODO pass along the partition boundaries too, so that summarizers can report summary values
          // aligned to the the bounds of the partition range (e.g. at the midpoint) 
          summarizePartition.summarizePart(part)
        } else { Seq() } // TODO this will skip empty parts, consider instead reporting an event with no value 
      }
    }
    Observable.from(summarizedEvents)
  }

  /** Partition the incoming events based on a division of the key range (e.g. time)
    * so that we can summarize each partition individually.
    */
  private def partitionNumericEvents[T: Numeric: TypeTag, U](events: Seq[Event[T, U]], partitions: Int, // format: OFF
      intervalOpt: Option[RangeInterval[T]]): Seq[Seq[Event[T, U]]] = { // format: ON
    if (events.isEmpty) {
      Seq()
    } else {
      val interval = intervalOpt.getOrElse { RangeInterval() }
      partitionIterator(events, interval, partitions).toSeq
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
  private def partitionIterator[T: TypeTag: Numeric, U]( // format: OFF
      events: Seq[Event[T, U]], interval: RangeInterval[T], partitions:Int)
      :Iterator[Seq[Event[T,U]]] =  { // format: ON
    val start = interval.start.getOrElse { events.head.argument }
    val (end, includeEnd) = interval.until match {
      case Some(explicitEnd) => (explicitEnd, false)
      case None              => (events.last.argument, true)
    }
    val range = end - start
    val step = stepSize(range, partitions)

    /** portion of the event sequence not yet consumed by the iterator */
    var remaining = events.filter(_.argument >= start) // filter in case the source data contains some extras

    /** end of the next partition to be consumed */
    var partStart = start

    // return an iterator that walks through the event sequence, one partition at a time
    new Iterator[Seq[Event[T, U]]] {
      override def hasNext: Boolean = !remaining.isEmpty && (partStart < end || (partStart == end && includeEnd))
      override def next(): Seq[Event[T, U]] = {
        val partEnd = partStart + step
        val (inPartition, remainder) = {
          if (partEnd >= end && includeEnd) { // last part
            remaining.span { event => event.argument <= partEnd }
          } else {
            remaining.span { event => event.argument < partEnd }
          }
        }
        remaining = remainder
        partStart = partStart + step
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

