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

/** min and max for keys and values */
case class KeyValueRanges[T, U](keyRange: MinMax[T], valueRange: MinMax[U])

/** a transform that returns the min and max of the keys and values of a column */
object KeyValueRangesTransform extends ColumnTransform {
  override def apply[T, U] // format: OFF
        (column: Column[T, U], transformParameters: JsObject) 
        (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    val events = column.readRangeOld() // all events, TODO take a range from the transform parameters

    implicit val keyOrdering = RecoverOrdering.ordering[T](column.keyType)
    implicit val valueOrdering = RecoverOrdering.ordering[U](column.valueType)
    implicit val keyFormat = RecoverJsonFormat.jsonFormat[T](column.keyType)
    implicit val valueFormat = RecoverJsonFormat.jsonFormat[U](column.valueType)

    val dataStream = events.initial.toSeq.map { seq =>
      if (!seq.isEmpty) {
        val keys = seq.map{ case Event(k, v) => k }
        val values = seq.map{ case Event(k, v) => v }
        val keysJson = JsArray("keyRange".toJson, MinMax(keys.min, keys.max).toJson)
        val valuesJson = JsArray("valueRange".toJson, MinMax(values.min, values.max).toJson)
        Seq(keysJson, valuesJson)
      } else {
        KeyValueRangesJson.Empty
      }
    }

    JsonDataStream(
      dataStream = dataStream,
      streamType = KeyValueType
    )
  }
}



