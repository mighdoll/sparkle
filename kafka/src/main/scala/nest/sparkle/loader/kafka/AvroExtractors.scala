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
import nest.sparkle.loader.kafka.SchemaExtractException.schemaExtractException
import org.apache.avro.generic.GenericData
import scala.collection.JavaConverters._

object AvroExtractors {
  def arrayRecords(schema: Schema,
                   arrayField: String = "elements",
                   idField: String = "id",
                   keyField: String = "time",
                   skipValueFields: Set[String] = Set()): IdKeyAndValuesExtractor = {

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
      schemaExtractException(s"$arrayField is not extractable from schmea $name")
    }

    val idType = typeTagField(schema, idField)
    val keyType = typeTagField(elementSchema, keyField)
    val captureValueFields = fieldsExcept(elementSchema, skipValueFields + keyField)
    val valueTypes = typeTagFields(elementSchema, captureValueFields)

    val types = IdKeyAndValuesTypes(idType, keyType, valueTypes)

    val idExtractor = fieldReader(idField)
    val keyExtractor = fieldReader(keyField)
    val valuesExtractor = multiFieldReader(captureValueFields)

    val extractor = recordWithArrayExtractor(arrayField, idExtractor, keyExtractor, valuesExtractor)

    IdKeyAndValuesExtractor(extractor, types)
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

  def recordWithArrayExtractor(arrayField: String, idExtractor: GenericRecord => Any, keyExtractor: GenericRecord => Any,
                               valuesExtractor: GenericRecord => Seq[Any]): GenericRecord => IdKeyAndValues = {
    (record: GenericRecord) =>
      val id = idExtractor(record)
      val array = record.get(arrayField).asInstanceOf[GenericData.Array[GenericData.Record]]
      val keysValues = {
        (0 until array.size).map { index =>
          val element = array.get(index)
          val key = keyExtractor(element)
          val values = valuesExtractor(element)
          (key, values)
        }
      }
      IdKeyAndValues(id, keysValues)
  }
}

case class SchemaExtractException(msg: String) extends RuntimeException(msg)
object SchemaExtractException {
  def schemaExtractException(msg: String) = throw SchemaExtractException(msg)
}

case class ArrayAndValueFields(arrayField: String, valueField: String)

case class IdKeyAndValuesExtractor(val extractor: GenericRecord => IdKeyAndValues,
                                   val types: IdKeyAndValuesTypes)

case class IdKeyAndValues(id: Any, keysValues: Seq[(Any, Seq[Any])])

case class IdKeyAndValuesTypes(idType: TypeTag[_], keyType: TypeTag[_], valueTypes: Iterable[TypeTag[_]])
