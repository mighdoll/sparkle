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
import spray.json._
import nest.sparkle.store.Column
import nest.sparkle.time.protocol.{ JsonDataStream, JsonEventWriter, KeyValueType, RangeParameters }
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.store.Event
import nest.sparkle.util.RecoverOrdering
import rx.lang.scala.Observable

/** match a SummaryTransform string selector, and return a ColumnTransform to be applied to each source column */
object SummaryTransform {
  def unapply(transform: String): Option[ColumnTransform] = {
    val lowerCase = transform.toLowerCase
    if (lowerCase.startsWith("summarize")) {
      lowerCase.stripPrefix("summarize") match {
        case "max"     => Some(SummarizeMax) // TODO replace with an actual summary
        case "min"     => ???
        case "linear"  => ???
        case "mean"    => ???
        case "average" => ???
        case "random"  => ???
        case "sample"  => ???
        case "uniques" => ???
        case "sum"     => ???
        case "count"   => ???
        case "rate"    => ???
        case _         => None
      }
    } else {
      None
    }
  }
}

/** Initial implementation of the SummaryMax transform
  * TODO support multiple summary transforms
  */
object SummarizeMax extends ColumnTransform {
  override def apply[T: JsonFormat, U: JsonWriter]( // format: OFF
       column: Column[T, U],
       transformParameters: JsObject
     ) (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    /** order events by the order of their values */
    implicit object ValueOrderEvents extends Ordering[Event[T, U]] {
      implicit val valueOrdering = RecoverOrdering.ordering[U](column.valueType)
      def compare(x: Event[T, U], y: Event[T, U]): Int = {
        valueOrdering.compare(x.value, y.value)
      }
    }

    /** find the maximum valued Event in a group of events */
    def max(data: Seq[Event[T, U]]): Seq[Event[T, U]] = {
      Seq(data.max)
    }
    // TODO handle edgeExtra

    /** read a range of column data, divide it into blocks, reduce each block
     *  into a summarized value, and return the result as a json stream */
    def summarize(params: RangeParameters[T], column: Column[T, U]): JsonDataStream = {
      val eventStream = column.readRange(params.start, params.end)
      val summarizedEvents = eventStream.toFutureSeq.map { events =>
        val groupSize = partitionSize(events, params)
        val groups = events.grouped(groupSize)
        groups.flatMap{ group => max(group) }.toSeq
      }
      val observableEvents = Observable.from(summarizedEvents)

      JsonDataStream(
        dataStream = JsonEventWriter.fromObservableSeq(observableEvents),
        streamType = KeyValueType
      )
    }

    Transform.rangeParameters(transformParameters) match {
      case Success(params) => summarize(params, column)
      case Failure(err)    => JsonDataStream.error(err)
    }
  }

  private def partitionSize[T, U](events: Seq[Event[T, U]], params: RangeParameters[T]): Int = {
    if (events.length < params.maxResults) {
      1
    } else {
      events.length / params.maxResults
    }
  }

}

