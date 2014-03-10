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

import spray.json.JsonWriter
import nest.sparkle.store.Column
import spray.json.JsObject
import nest.sparkle.time.protocol.JsonDataStream
import scala.concurrent.ExecutionContext
import nest.sparkle.store.Event
import spray.json._
import spray.json.DefaultJsonProtocol._
import nest.sparkle.time.protocol.JsonEventWriter
import nest.sparkle.time.protocol.KeyValueType

/** Convert a MinMax to/from a two element json array */
object MinMaxJson extends DefaultJsonProtocol {
  implicit def MinMaxFormat[T: JsonWriter]: JsonWriter[MinMax[T]] = {
    new JsonWriter[MinMax[T]] {
      def write(minMax: MinMax[T]): JsValue = {
        JsArray(minMax.min.toJson, minMax.max.toJson)
      }
    }
  }

  implicit def MinMaxReader[T: JsonReader]: JsonReader[MinMax[T]] = {
    new JsonReader[MinMax[T]] {
      def read(value: JsValue): MinMax[T] = {
        value match {
          case JsArray(min :: max :: Nil) =>
            MinMax(min.convertTo[T], max.convertTo[T])
          case x => throw new DeserializationException(s"MinMax expected, got $x")
        }
      }
    }
  }
}

/** Convert a DomainRange to json in the following shape:
  *
  * [
  * ["domain", [0, 100]],
  * ["range", [2.1, 2.94]]
  * ]
  *
  */
object DomainRangeJson extends DefaultJsonProtocol {
  import MinMaxJson._
  /** json writer for DomainRangeLimits */
  implicit def DomainRangeFormat[T: JsonWriter, U: JsonWriter]: JsonWriter[DomainRangeLimits[T, U]] = {
    new JsonWriter[DomainRangeLimits[T, U]] {
      def write(limits: DomainRangeLimits[T, U]): JsValue = {
        val domainProperty = JsArray("domain".toJson, limits.domain.toJson)
        val rangeProperty = JsArray("range".toJson, limits.range.toJson)
        JsArray(domainProperty, rangeProperty)
      }
    }
  }

  /** json reader for DomainRangeLimits */
  implicit def DomainRangeReader[T: JsonReader, U: JsonReader]: JsonReader[DomainRangeLimits[T, U]] = {
    new JsonReader[DomainRangeLimits[T, U]] {
      def read(value: JsValue): DomainRangeLimits[T, U] = {
        value match {
          case JsArray(
                    JsArray(JsString("domain") :: List(domainJs)) 
                 :: JsArray(JsString("range") :: List(rangeJs)) 
                 :: Nil                 
               ) =>
            val domain = domainJs.convertTo[MinMax[T]]
            val range = rangeJs.convertTo[MinMax[U]]
            DomainRangeLimits(domain,range)
          case x => throw new DeserializationException(s"DomainRangeLimits expected, got $x")
        }
      }
    }
  }

  /** the json representation of DomainRangeLimits on an empty column */
  val EmptyJson = JsArray(
    JsArray("domain".toJson, JsArray()),
    JsArray("range".toJson, JsArray())
  )

}

/** minimum and maximum values */
case class MinMax[T](min: T, max: T)

/** min and max for domain and range */
case class DomainRangeLimits[T, U](domain: MinMax[T], range: MinMax[U])

/** a transform that returns the min and max of the domain and range of a column */
object DomainRange extends ColumnTransform {
  import DomainRangeJson._
  override def apply[T: JsonWriter: Ordering, U: JsonWriter: Ordering](column: Column[T, U], transformParameters: JsObject) // format: OFF
        (implicit execution: ExecutionContext): JsonDataStream = { // format: ON

    val events = column.readRange() // all events, TODO take a range from the transform parameters

    val dataStream = events.toSeq.map { seq =>
      if (!seq.isEmpty) {
        val domain = seq.map{ case Event(k, v) => k }
        val range = seq.map{ case Event(k, v) => v }
        val limits = DomainRangeLimits(MinMax(domain.min, domain.max), MinMax(range.min, range.max))
        Seq(limits.toJson.asInstanceOf[JsArray])
      } else {
        Seq(DomainRangeJson.EmptyJson)
      }
    }

    JsonDataStream(
      dataStream = dataStream,
      streamType = KeyValueType
    )
  }
}

/** extractor to match a DomainRange transform by string name */
object DomainRangeTransform {
  def unapply(transform: String): Option[ColumnTransform] = {
    transform.toLowerCase match {
      case "domainrange" => Some(DomainRange)
    }
  }
}


