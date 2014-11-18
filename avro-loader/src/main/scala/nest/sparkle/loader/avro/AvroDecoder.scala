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
package nest.sparkle.loader.avro

import nest.sparkle.loader.ArrayRecordMeta
import nest.sparkle.store.Event
import org.apache.avro.Schema

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.language.existentials
import scala.reflect.runtime.universe._

/**
 * AvroDecoder defines a set of operations for AvroDecoders to leverage for
 * walking through Avro events creating ArrayRecordDecoders
 */
trait AvroDecoder {
  type ArrayRecordFilter = (ArrayRecordMeta, Seq[Seq[Event[_, _]]]) => Seq[Seq[Event[_, _]]]

  protected def typeTagField(schema: Schema, fieldName: String): TypeTag[_] = {
    fieldTypeTag(schema.getField(fieldName))
  }

  /** return the TypeTag corresponding to a field in an Avro Schema */
  protected def fieldTypeTag(avroField: Schema.Field): TypeTag[_] = {
    avroField.schema.getType match {
      case Schema.Type.LONG    => typeTag[Long]
      case Schema.Type.INT     => typeTag[Int]
      case Schema.Type.DOUBLE  => typeTag[Double]
      case Schema.Type.BOOLEAN => typeTag[Boolean]
      case Schema.Type.STRING  => typeTag[String]
      case Schema.Type.FLOAT   => typeTag[Float]
      case Schema.Type.UNION   => unionTypeTag(avroField.schema)
      case _                   => ???
    }
  }

  /** avroField is a union. Only unions with null and a primitive are supported.
    * Find the primitive and return it.
    * Supports either order, e.g. [null,string] or [string,null]
    * @param schema union schema
    * @return typeTag of primitive in the union.
    */
  protected def unionTypeTag(schema: Schema): TypeTag[_] = {
    val schemas = schema.getTypes.toSeq
    if (schemas.size != 2) {
      SchemaDecodeException.schemaDecodeException("avro field is a union of more than 2 types")
    }
    val types = schemas.map(_.getType)
    if (!types.contains(Schema.Type.NULL)) {
      SchemaDecodeException.schemaDecodeException("avro field is a union without a null")
    }

    // Find the first supported type, really should only be skipping NULL.
    types.collectFirst({
      case Schema.Type.LONG    => typeTag[Long]
      case Schema.Type.INT     => typeTag[Int]
      case Schema.Type.DOUBLE  => typeTag[Double]
      case Schema.Type.BOOLEAN => typeTag[Boolean]
      case Schema.Type.STRING  => typeTag[String]
      case Schema.Type.FLOAT   => typeTag[Float]
    }).getOrElse(SchemaDecodeException.schemaDecodeException("avro field is a union without a supported type"))
  }

  protected def fieldsExcept(schema: Schema, exceptFields: Set[String]): Seq[String] = {
    schema.getFields.asScala.map(_.name).collect {
      case name if !exceptFields.contains(name) => name
    }
  }

  protected def typeTagFields(schema: Schema, fields: Seq[String]): Iterable[TypeTag[_]] = {
    fields.map { fieldName =>
      val schemaField = schema.getField(fieldName)
      fieldTypeTag(schemaField)
    }
  }
}

/** failure in configuring the avro schema */
case class SchemaDecodeException(msg: String) extends RuntimeException(msg)
object SchemaDecodeException {
  def schemaDecodeException(msg: String): Nothing = throw SchemaDecodeException(msg)
}
