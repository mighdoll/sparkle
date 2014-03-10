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
import nest.sparkle.time.protocol.KeyValueType
import nest.sparkle.time.protocol.JsonEventWriter
import nest.sparkle.time.protocol.JsonDataStream
import nest.sparkle.store.Column
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.store.Event
import spray.json._
import spray.json.DefaultJsonProtocol._
import scala.concurrent.Future
import nest.sparkle.store.LongDoubleColumn
import nest.sparkle.store.LongLongColumn

/** A function that constructs a JsonDataStream.  The JsonDataStream will transform a single
  * source column into a json output column on demand.
  */
abstract class ColumnTransform {
  def apply[T: JsonWriter: Ordering, U: JsonWriter: Ordering](column: Column[T, U],
                                                              transformParameters: JsObject)(implicit execution: ExecutionContext): JsonDataStream
}

object StandardColumnTransform {
  /** Given an untyped future Column, call a transform function with the type specific column
   *  when the future completes. */
  def executeTypedTransform(futureColumns: Future[Seq[Column[_, _]]], // format: OFF
      columnTransform:ColumnTransform, transformParameters:JsObject) 
      (implicit execution: ExecutionContext):Future[Seq[JsonDataStream]] = { // format: ON
    futureColumns.map { columns =>
      columns.map {
        case LongDoubleColumn(castColumn) => columnTransform(castColumn, transformParameters)
        case LongLongColumn(castColumn)   => columnTransform(castColumn, transformParameters)
      }
    }
  }
}

/** match a SummaryTransform string selector, and return a ColumnTransform to be applied to each source column */
object SummaryTransform {
  def unapply(transform: String): Option[ColumnTransform] = {
    val lowerCase = transform.toLowerCase
    if (lowerCase.startsWith("summarize")) {
      lowerCase.stripPrefix("summarize") match {
        case "max"     => Some(JustCopy) // TODO replace with an actual summary
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

/** a transform that copies the array of source columns to json encodable output streams */
object JustCopy extends ColumnTransform {
  override def apply[T: JsonWriter: Ordering, U: JsonWriter: Ordering]( // format: OFF
       column: Column[T, U],
       transformParameters: JsObject
     ) (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    /** return an outputStream that can produce a column */
    val events = column.readRange() // just copy the entire source column to the output  
    JsonDataStream(
      dataStream = JsonEventWriter(events),
      streamType = KeyValueType
    )
  }
}

