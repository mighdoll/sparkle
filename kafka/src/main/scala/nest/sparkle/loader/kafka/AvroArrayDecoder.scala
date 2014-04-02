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

package nest.sparkle.loader.kafka

import org.apache.avro.generic.GenericRecord
import org.apache.avro.Schema
import scala.reflect.runtime.universe._
import scala.language.existentials
import nest.sparkle.loader.kafka.SchemaDecodeException.schemaDecodeException
import org.apache.avro.generic.GenericData
import scala.collection.JavaConverters._

/** Utility for making the Decoder part of an AvroColumnDecoder from an avro schema when
 *  the avro schema contains an array of key,values fields. */
object AvroArrayDecoder {

  /** return a decoder for an avro schema containing an id, and an array of key,[value,..] records.
   *  There can be more than one value in each row of the array.  The value fields are copied
   *  from the avro schema field names.  
   *  
   *  See `MillisDoubleArrayAvro` in the tests for an example avro schema in the expected structure.
   *   
   *  @skipValueFields a blacklist of values fields in each array row to ignore
   */
  def decoder(schema: Schema,
              arrayField: String = "elements",
              idField: String = "id",
              keyField: String = "time",
              skipValueFields: Set[String] = Set()): IdKeyAndValuesDecoder = {

    val name = schema.getName

    val elementSchemaOpt =
      for {
        arrayRecordSchema <- Option(schema.getField(arrayField)).map(_.schema)
        if (arrayRecordSchema.getType == Schema.Type.ARRAY)
        arrayElementSchema <- Option(arrayRecordSchema.getElementType)
      } yield {
        arrayElementSchema
      }

    val elementSchema = elementSchemaOpt.getOrElse {
      schemaDecodeException(s"$arrayField is not extractable from schema $name")
    }

    val idType = typeTagField(schema, idField)
    val keyType = typeTagField(elementSchema, keyField)
    val captureValueFields = fieldsExcept(elementSchema, skipValueFields + keyField)
    val valueTypes = typeTagFields(elementSchema, captureValueFields)

    val types = IdKeyAndValuesTypes(idType, keyType, valueTypes)

    val idDecoder = fieldReader(idField)
    val keyDecoder = fieldReader(keyField)
    val valuesDecoder = multiFieldReader(captureValueFields)

    val decoder = recordWithArrayDecoder(arrayField, idDecoder, keyDecoder, valuesDecoder)

    IdKeyAndValuesDecoder(decoder, types)
  }

  private def typeTagField(schema: Schema, fieldName: String): TypeTag[_] = {
    fieldTypeTag(schema.getField(fieldName))
  }

  private def fieldTypeTag(avroField: Schema.Field): TypeTag[_] = {
    avroField.schema.getType match {
      case Schema.Type.LONG    => typeTag[Long]
      case Schema.Type.INT     => typeTag[Int]
      case Schema.Type.DOUBLE  => typeTag[Double]
      case Schema.Type.BOOLEAN => typeTag[Boolean]
      case Schema.Type.STRING  => typeTag[String]
      case Schema.Type.FLOAT   => typeTag[Float]
      case _                   => ???
    }
  }

  private def fieldsExcept(schema: Schema, exceptFields: Set[String]): Seq[String] = {
    schema.getFields.asScala.map(_.name).collect {
      case name if !exceptFields.contains(name) => name
    }
  }

  private def typeTagFields(schema: Schema, fields: Seq[String]): Iterable[TypeTag[_]] = {
    fields.map { fieldName =>
      val schemaField = schema.getField(fieldName)
      fieldTypeTag(schemaField)
    }
  }

  private def fieldReader(fieldName: String): GenericRecord => Any = { record: GenericRecord =>
    record.get(fieldName)
  }

  private def multiFieldReader(fieldNames: Seq[String]): GenericRecord => Seq[Any] = {
    record: GenericRecord =>
      fieldNames.map{ name => record.get(name) }
  }

  private def recordWithArrayDecoder(arrayField: String, idDecodeor: GenericRecord => Any, keyDecodeor: GenericRecord => Any,
                                       valuesDecodeor: GenericRecord => Seq[Any]): GenericRecord => IdKeyAndValues = {
    (record: GenericRecord) =>
      val id = idDecodeor(record)
      val array = record.get(arrayField).asInstanceOf[GenericData.Array[GenericData.Record]]
      val keysValues = {
        (0 until array.size).map { index =>
          val element = array.get(index)
          val key = keyDecodeor(element)
          val values = valuesDecodeor(element)
          (key, values)
        }
      }
      IdKeyAndValues(id, keysValues)
  }
}

/** failure in configuring the avro schema */
case class SchemaDecodeException(msg: String) extends RuntimeException(msg)
object SchemaDecodeException {
  def schemaDecodeException(msg: String) = throw SchemaDecodeException(msg)
}

case class IdKeyAndValuesDecoder(val decodeRecord: GenericRecord => IdKeyAndValues,
                                 val types: IdKeyAndValuesTypes)

case class IdKeyAndValues(id: Any, keysValues: Seq[(Any, Seq[Any])])

case class IdKeyAndValuesTypes(idType: TypeTag[_], keyType: TypeTag[_], valueTypes: Iterable[TypeTag[_]])
