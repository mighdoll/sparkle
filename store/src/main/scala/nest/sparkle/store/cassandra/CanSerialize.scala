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

import java.nio.ByteBuffer

import scala.concurrent.duration.FiniteDuration
import scala.reflect.runtime.universe._
import scala.concurrent.duration.NANOSECONDS
import com.datastax.driver.core.{DataType, Row}
import spray.json._
import nest.sparkle.util.GenericFlags

/** a cassandra serializer/deserializer */
abstract class CanSerialize[T: TypeTag] {
  /** return the cassandra data type */
  def dataType: DataType

  /** return the cassandra data type as a String */
  def columnType: String = dataType.getName.toString

  /** return a string representation of the stored scala type */
  def nativeType: String = implicitly[TypeTag[T]].tpe.toString

  /** serialize a scala native type into an AnyRef that maps directly to a cassandra data type */
  def serialize(value: T): AnyRef = value.asInstanceOf[AnyRef]

  /** deserialize a scala native type from a cassandra row */
  def fromRow(row: Row, index: Int): T

  /** return the TypeTag for the type we CanSerialize */
  def typedTag: TypeTag[T] = typeTag[T]
}

/** standard serializers for cassandra data types */
object serializers {
  implicit object LongSerializer extends CanSerialize[Long] {
    val dataType = DataType.bigint()
    def deserialize(serialized: AnyRef): Long = serialized.asInstanceOf[Long]
    def fromRow(row: Row, index: Int): Long = {
      row.getLong(index)
    }
  }

  implicit object NanoTimeSerializer extends CanSerialize[NanoTime] {
    val dataType = DataType.bigint()
    override def serialize(value: NanoTime): AnyRef = value.nanos.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): NanoTime = {
      NanoTime(row.getLong(index))
    }
  }

  implicit object MilliTimeSerializer extends CanSerialize[MilliTime] {
    val dataType = DataType.bigint()
    override def serialize(value: MilliTime): AnyRef = value.millis.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): MilliTime = {
      MilliTime(row.getLong(index))
    }
  }

  implicit object FiniteDurationSerializer extends CanSerialize[FiniteDuration] {
    val dataType = DataType.bigint()
    override def serialize(value: FiniteDuration): AnyRef = value.toNanos.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): FiniteDuration = {
      FiniteDuration(row.getLong(index), NANOSECONDS)
    }
  }

  implicit object DoubleSerializer extends CanSerialize[Double] {
    val dataType = DataType.cdouble()
    def deserialize(serialized: AnyRef): Double =
      serialized.asInstanceOf[Double]
    def fromRow(row: Row, index: Int): Double = {
      row.getDouble(index)
    }
  }

  implicit object IntSerializer extends CanSerialize[Int] {
    val dataType = DataType.cint()
    def fromRow(row: Row, index: Int): Int = {
      row.getInt(index)
    }
  }

  implicit object ShortSerializer extends CanSerialize[Short] {
    val dataType = DataType.cint()
    def fromRow(row: Row, index: Int): Short = {
      row.getInt(index).toShort
    }
    override def serialize(value: Short): AnyRef = value.toInt.asInstanceOf[AnyRef]
  }

  implicit object BooleanSerializer extends CanSerialize[Boolean] {
    val dataType = DataType.cboolean()
    def fromRow(row: Row, index: Int): Boolean = {
      row.getBool(index)
    }
  }

  implicit object CharSerializer extends CanSerialize[Char] {
    val dataType = DataType.text()
    def fromRow(row: Row, index: Int): Char = {
      row.getString(index)(0)
    }
    override def serialize(value: Char): AnyRef = value.toString
  }

  implicit object StringSerializer extends CanSerialize[String] {
    val dataType = DataType.text()
    def fromRow(row: Row, index: Int): String = {
      row.getString(index)
    }
  }

  implicit object AsciiSerializer extends CanSerialize[AsciiString] {
    val dataType = DataType.ascii()
    def fromRow(row: Row, index: Int): AsciiString = {
      AsciiString(row.getString(index))
    }
  }

  implicit object JsonSerializer extends CanSerialize[JsValue] {
    val dataType = DataType.text()
    def fromRow(row: Row, index: Int): JsValue = {
      row.getString(index).parseJson
    }
    override def serialize(value: JsValue): AnyRef =
      value.prettyPrint
  }

  implicit object GenericFlagsSerializer extends CanSerialize[GenericFlags] {
    val dataType = DataType.bigint()
    override def serialize(flags: GenericFlags): AnyRef = flags.value.asInstanceOf[AnyRef]
    def fromRow(row: Row, index: Int): GenericFlags = {
      GenericFlags(row.getLong(index))
    }
  }

  implicit object ByteBufferSerializer extends CanSerialize[ByteBuffer] {
    val dataType = DataType.blob()
    def fromRow(row: Row, index: Int): ByteBuffer = {
      val buf = row.getBytes(index)
      val fixedBuf = new Array[Byte](buf.remaining())
      buf.get(fixedBuf)
      ByteBuffer.wrap(fixedBuf)
    }
  }

}
