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

abstract class CanSerialize[T: TypeTag] {
  def columnType: String

  def nativeType: String = {
    val x = implicitly[TypeTag[T]]
    x.tpe.toString()
  }
  def serialize(value: T): AnyRef = value.asInstanceOf[AnyRef]
  def fromRow(row: Row, index: Int): T 

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
