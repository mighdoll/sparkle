/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.store.cassandra

import scala.concurrent.duration.FiniteDuration
import scala.reflect.runtime.universe._
import scala.concurrent.duration.NANOSECONDS
import com.datastax.driver.core.Row
import spray.json._
import nest.sparkle.util.GenericFlags
import nest.sparkle.util.Exceptions.NYI

/** utilities for converting between typeTags and cassandra serialized nativeType strings */
object CanSerialize {
  /** return a TypeTag from cassandra serialized nativeType string */
  def stringToTypeTag(typeString: String): TypeTag[_] = {
    typeString match {
      case "Boolean"                        => typeTag[Boolean]
      case "Short"                          => typeTag[Short]
      case "Int"                            => typeTag[Int]
      case "Long"                           => typeTag[Long]
      case "Double"                         => typeTag[Double]
      case "Char"                           => typeTag[Char]
      case "String"                         => typeTag[String]
      case "spray.json.JsValue"             => typeTag[JsValue]
      case "nest.sparkle.util.GenericFlags" => typeTag[GenericFlags]
      case x                                => NYI(s"unsupported storage type $x")
    }
  }
}

/** a cassandra serializer/deserializer */
abstract class CanSerialize[T: TypeTag] {
  /** return the cassandra data type */
  def columnType: String

  /** return a string representation of the stored scala type */
  def nativeType: String = implicitly[TypeTag[T]].tpe.toString

  /** serialize a scala native type into an AnyRef that maps directly to a cassandra data type */
  def serialize(value: T): AnyRef = value.asInstanceOf[AnyRef]

  /** deserialize a scala native type from a cassandra row */
  def fromRow(row: Row, index: Int): T

  /** return the TypeTag for the type we CanSerialize */
  def typedTag: TypeTag[T] = typeTag[T]
}

// TODO: support registering of custom serializers
/** standard serializers for cassandra data types */
object serializers {
  implicit object LongSerializer extends CanSerialize[Long] {
    val columnType = "bigint"
    def deserialize(serialized: AnyRef): Long = serialized.asInstanceOf[Long]
    def fromRow(row: Row, index: Int): Long = {
      row.getLong(index)
    }
  }

  implicit object NanoTimeSerializer extends CanSerialize[NanoTime] {
    val columnType = "bigint"
    override def serialize(value: NanoTime): AnyRef = value.nanos.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): NanoTime = {
      NanoTime(row.getLong(index))
    }
  }

  implicit object MilliTimeSerializer extends CanSerialize[MilliTime] {
    val columnType = "bigint"
    override def serialize(value: MilliTime): AnyRef = value.millis.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): MilliTime = {
      MilliTime(row.getLong(index))
    }
  }

  implicit object FiniteDurationSerializer extends CanSerialize[FiniteDuration] {
    val columnType = "bigint"
    override def serialize(value: FiniteDuration): AnyRef = value.toNanos.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): FiniteDuration = {
      FiniteDuration(row.getLong(index), NANOSECONDS)
    }
  }

  implicit object DoubleSerializer extends CanSerialize[Double] {
    val columnType = "double"
    def deserialize(serialized: AnyRef): Double =
      serialized.asInstanceOf[Double]
    def fromRow(row: Row, index: Int): Double = {
      row.getDouble(index)
    }
  }

  implicit object IntSerializer extends CanSerialize[Int] {
    val columnType = "int"
    def fromRow(row: Row, index: Int): Int = {
      row.getInt(index)
    }
  }

  implicit object ShortSerializer extends CanSerialize[Short] {
    val columnType = "int"
    def fromRow(row: Row, index: Int): Short = {
      row.getInt(index).toShort
    }
  }

  implicit object BooleanSerializer extends CanSerialize[Boolean] {
    val columnType = "boolean"
    def fromRow(row: Row, index: Int): Boolean = {
      row.getBool(index)
    }
  }

  implicit object CharSerializer extends CanSerialize[Char] {
    val columnType = "text"
    def fromRow(row: Row, index: Int): Char = {
      row.getString(index)(0)
    }
    override def serialize(value: Char): AnyRef = value.toString
  }

  implicit object StringSerializer extends CanSerialize[String] {
    val columnType = "text"
    def fromRow(row: Row, index: Int): String = {
      row.getString(index)
    }
  }

  implicit object AsciiSerializer extends CanSerialize[AsciiString] {
    val columnType = "ascii"
    def fromRow(row: Row, index: Int): AsciiString = {
      AsciiString(row.getString(index))
    }
  }

  implicit object JsonSerializer extends CanSerialize[JsValue] {
    val columnType = "text"

    def fromRow(row: Row, index: Int): JsValue = {
      row.getString(index).asJson
    }

    override def serialize(value: JsValue): AnyRef =
      value.prettyPrint
  }

  /** until we can register custom serializers, we'll use GenericFlags to
    * serialize all Flags types, and have the client convert GenericFlags to
    * custom Flags types */
  implicit object GenericFlagsSerializer extends CanSerialize[GenericFlags] {
    val columnType = "bigint"
    override def serialize(flags: GenericFlags): AnyRef = flags.value.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): GenericFlags = {
      GenericFlags(row.getLong(index))
    }
  }

}
