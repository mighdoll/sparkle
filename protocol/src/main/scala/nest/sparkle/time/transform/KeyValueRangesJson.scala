package nest.sparkle.time.transform

import spray.json._

/** Convert a MinMax to/from a two element json array */
object MinMaxJson extends DefaultJsonProtocol {
  implicit def MinMaxFormat[T: JsonFormat]: JsonFormat[MinMax[T]] = {
    new JsonFormat[MinMax[T]] {
      def write(minMax: MinMax[T]): JsValue = {
        JsArray(minMax.min.toJson, minMax.max.toJson)
      }
      
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

import MinMaxJson.MinMaxFormat

/** Convert a KeyValueRanges to json in the following shape:
  *
  * [
  * ["keyRange", [0, 100]],
  * ["valueRange", [2.1, 2.94]]
  * ]
  *
  */
object KeyValueRangesJson extends DefaultJsonProtocol {
  /** json read/write formatting KeyValueRanges */
  implicit def KeyValueRangesFormat[T: JsonFormat, U: JsonFormat]: JsonFormat[KeyValueRanges[T, U]] = {
    new JsonFormat[KeyValueRanges[T, U]] {
      def write(limits: KeyValueRanges[T, U]): JsValue = {
        val keyRangeProperty = JsArray("keyRange".toJson, limits.keyRange.toJson)
        val valueRangeProperty = JsArray("valueRange".toJson, limits.valueRange.toJson)
        JsArray(keyRangeProperty, valueRangeProperty)
      }
      
      def read(value: JsValue): KeyValueRanges[T, U] = {
        value match {
          case JsArray(
            JsArray(JsString("keyRange") :: List(keyRangeJs))
              :: JsArray(JsString("valueRange") :: List(valueRangeJs))
              :: Nil
            ) =>
            val keyRange = keyRangeJs.convertTo[MinMax[T]]
            val valueRange = valueRangeJs.convertTo[MinMax[U]]
            KeyValueRanges(keyRange, valueRange)
          case x => throw new DeserializationException(s"KeyValueRanges expected, got $x")
        }
      }

    }
  }

  /** the json representation of KeyValueRanges on an empty column */
  val Empty = Seq(
    JsArray("keyRange".toJson, JsArray()),
    JsArray("valueRange".toJson, JsArray())
  )
}
