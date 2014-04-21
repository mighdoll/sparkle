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
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.concurrent.duration.NANOSECONDS
import com.datastax.driver.core.Row

/** utilities for converting between typeTags and cassandra serialized nativeType strings */
object CanSerialize {
  /** return a TypeTag from cassandra serialized nativeType string */
  def stringToTypeTag(typeString: String): TypeTag[_] = {
    typeString match {
      case "Double"  => typeTag[Double]
      case "Long"    => typeTag[Long]
      case "String"  => typeTag[String]
      case "Boolean" => typeTag[Boolean]
      case "Int"     => typeTag[Int]
      case _         => ???
    }
  }

  /** return nativeType string from a TypeTag */
  def typeTagToString(typeTag: TypeTag[_]): String = {
    typeTag.tpe.toString
  }
}

/** a cassandra serializer/deserializer */
abstract class CanSerialize[T: TypeTag] {
  /** return the cassandra data type */
  def columnType: String

  /** return a string representation of the stored scala type */
  def nativeType: String = CanSerialize.typeTagToString(implicitly[TypeTag[T]])
  
  /** serialize a scala native type into an AnyRef that maps directly to a cassandra data type */
  def serialize(value: T): AnyRef = value.asInstanceOf[AnyRef]

  /** deserialize a scala native type from a cassandra row */
  def fromRow(row: Row, index: Int): T

  /** return the TypeTag for the type we CanSerialize */
  def typedTag:TypeTag[T] = typeTag[T]
}

/** standard serializers for cassandra data types */
object serializers {
  implicit object LongSerializer extends CanSerialize[Long] {
    def columnType = "bigint"
    def deserialize(serialized: AnyRef): Long = serialized.asInstanceOf[Long]
    def fromRow(row: Row, index: Int): Long = {
      row.getLong(index)
    }
  }
  
  implicit object NanoTimeSerializer extends CanSerialize[NanoTime] {
    def columnType = "bigint"
    override def serialize(value: NanoTime) = value.nanos.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): NanoTime = {
      NanoTime(row.getLong(index))
    }
  }
  
  implicit object MilliTimeSerializer extends CanSerialize[MilliTime] {
    def columnType = "bigint"
    override def serialize(value: MilliTime) = value.millis.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): MilliTime = {
      MilliTime(row.getLong(index))
    }
  }

  implicit object FiniteDurationSerializer extends CanSerialize[FiniteDuration] {
    def columnType = "bigint"
    override def serialize(value: FiniteDuration) = value.toNanos.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): FiniteDuration = {
      FiniteDuration(row.getLong(index), NANOSECONDS)
    }
  }
  
  implicit object DoubleSerializer extends CanSerialize[Double] {
    def columnType = "double"
    def deserialize(serialized: AnyRef): Double =
      serialized.asInstanceOf[Double]
    def fromRow(row: Row, index: Int): Double = {
      row.getDouble(index)
    }
  }

  implicit object IntSerializer extends CanSerialize[Int] {
    def columnType = "int"
    def fromRow(row: Row, index: Int): Int = {
      row.getInt(index)
    }
  }

  implicit object BooleanSerializer extends CanSerialize[Boolean] {
    def columnType = "boolean"
    def fromRow(row: Row, index: Int): Boolean = {
      row.getBool(index)
    }
  }
  
  implicit object StringSerializer extends CanSerialize[String] {
    def columnType = "text"
    def fromRow(row: Row, index: Int): String = {
      row.getString(index)
    }
  }
  
  implicit object AsciiSerializer extends CanSerialize[AsciiString] {
    def columnType = "ascii"
    def fromRow(row: Row, index: Int): AsciiString = {
      AsciiString(row.getString(index))
    }
  }

}
