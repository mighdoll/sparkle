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
          case JsArray(Vector(min, max)) =>
            MinMax(min.convertTo[T], max.convertTo[T])
          case x => throw new DeserializationException(s"MinMax expected, got $x")
        }
      }
    }
  }
}

import MinMaxJson.MinMaxFormat

/** Convert a DomainRange to json in the following shape:
  *
  * [
  * ["domain", [0, 100]],
  * ["range", [2.1, 2.94]]
  * ]
  *
  */
object DomainRangeJson extends DefaultJsonProtocol {
  /** json read/write formating DomainRange */
  implicit def DomainRangeFormat[T: JsonFormat, U: JsonFormat]: JsonFormat[DomainRange[T, U]] = {
    new JsonFormat[DomainRange[T, U]] {
      def write(limits: DomainRange[T, U]): JsValue = {
        val domainProperty = JsArray("domain".toJson, limits.domain.toJson)
        val rangeProperty = JsArray("range".toJson, limits.range.toJson)
        JsArray(domainProperty, rangeProperty)
      }
      
      def read(value: JsValue): DomainRange[T, U] = {
        value match {
          case JsArray(
            Vector(
              JsArray(Vector(JsString("domain"), domainJs)),
              JsArray(Vector(JsString("range"), rangeJs))
            )
          ) =>
            val domain = domainJs.convertTo[MinMax[T]]
            val range = rangeJs.convertTo[MinMax[U]]
            DomainRange(domain, range)
          case x => throw new DeserializationException(s"DomainRange expected, got $x")
        }
      }

    }
  }

  /** the json representation of DomainRangeLimits on an empty column */
  val Empty = Seq(
    JsArray("domain".toJson, JsArray()),
    JsArray("range".toJson, JsArray())
  )
}
