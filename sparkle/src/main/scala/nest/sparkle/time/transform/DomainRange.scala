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
import spray.json.DefaultJsonProtocol._

import nest.sparkle.store.{Column, Event}
import nest.sparkle.time.protocol.{JsonDataStream, KeyValueType}
import nest.sparkle.time.transform.MinMaxJson.MinMaxFormat
import nest.sparkle.util.{RecoverJsonFormat, RecoverOrdering}


/** minimum and maximum values */
case class MinMax[T](min: T, max: T)

/** min and max for domain and range */
case class DomainRange[T, U](domain: MinMax[T], range: MinMax[U])

/** a transform that returns the min and max of the domain and range of a column */
object DomainRangeTransform extends ColumnTransform {
  override def apply[T, U] // format: OFF
        (column: Column[T, U], transformParameters: JsObject) 
        (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    val events = column.readRange() // all events, TODO take a range from the transform parameters

    implicit val keyOrdering = RecoverOrdering.ordering[T](column.keyType)
    implicit val valueOrdering = RecoverOrdering.ordering[U](column.valueType)
    implicit val keyFormat = RecoverJsonFormat.jsonFormat[T](column.keyType)
    implicit val valueFormat = RecoverJsonFormat.jsonFormat[U](column.valueType)

    val dataStream = events.initial.toSeq.map { seq =>
      if (!seq.isEmpty) {
        val domain = seq.map{ case Event(k, v) => k }
        val range = seq.map{ case Event(k, v) => v }
        val limits = DomainRange(MinMax(domain.min, domain.max), MinMax(range.min, range.max))
        val domainJson = JsArray("domain".toJson, MinMax(domain.min, domain.max).toJson)
        val rangeJson = JsArray("range".toJson, MinMax(range.min, range.max).toJson)
        Seq(domainJson, rangeJson)
      } else {
        DomainRangeJson.Empty
      }
    }

    JsonDataStream(
      dataStream = dataStream,
      streamType = KeyValueType
    )
  }
}



