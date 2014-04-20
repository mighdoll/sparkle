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
import nest.sparkle.store.Event
import nest.sparkle.util.Log

/** Utility for making the Decoder part of an AvroColumnDecoder from an avro schema when
  * the avro schema contains an array of key,values fields.
  */
object AvroArrayDecoder extends Log {

  /** return a decoder for an avro schema containing an id, and an array of key,[value,..] records.
    * There can be more than one value in each row of the array.  The value fields are copied
    * from the avro schema field names.
    *
    * See `MillisDoubleArrayAvro` in the tests for an example avro schema in the expected structure.
    *
    * @skipValueFields a blacklist of values fields in each array row to ignore
    */
  def decoder(schema: Schema,
              arrayField: String = "elements",
              idField: String = "id",
              keyField: String = "time",
              skipValueFields: Set[String] = Set()): ArrayRecordDecoder = {

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

    val metaData = {
      val idType = typeTagField(schema, idField)
      val keyType = typeTagField(elementSchema, keyField)
      val key = NameAndType(keyField, keyType)
      val valueFields = fieldsExcept(elementSchema, skipValueFields + keyField)
      val valueTypes = typeTagFields(elementSchema, valueFields)
      val values: Seq[NameAndType] = valueFields zip valueTypes map {
        case (name, typed) =>
          NameAndType(name, typed)
      }
      ArrayRecordMeta(values = values, key = key, idType = idType)
    }

    val decoder = recordWithArrayDecoder(idField, arrayField, metaData)

    ArrayRecordDecoder(decoder, metaData)
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

  /** return a function that reads a Generic record into the ArrayRecordColumns format */
  private def recordWithArrayDecoder(idField: String,
                                     arrayField: String,
                                     metaData: ArrayRecordMeta): GenericRecord => ArrayRecordColumns = {

    (record: GenericRecord) =>
      val id = record.get(idField)
      val array = record.get(arrayField).asInstanceOf[GenericData.Array[GenericData.Record]]
      log.info(s"reading record for id: $id containing ${array.size} elements")

      /** map over all the rows in the array embedded in the record */
      def mapRows[T](fn: GenericData.Record => T): Seq[T] = {
        (0 until array.size).map { rowDex =>
          fn(array.get(rowDex))
        }
      }

      // collect all values in column format 
      val valueColumns: Seq[Seq[Any]] = {
        val valueNames = metaData.values.map{ _.name }
        valueNames.map{ name =>
          mapRows{ row =>
            row.get(name)
          }
        }
      }

      // single column of just the keys
      val keys: Seq[Any] = {
        mapRows { row =>
          row.get(metaData.key.name)
        }
      }

      // convert to event column format
      val columns: Seq[Seq[Event[_, _]]] = {
        valueColumns.map { values =>
          keys zip values map {
            case (key, value) =>
              Event(key, value)
          }
        }
      }

      ArrayRecordColumns(id, columns)
  }
}

/** failure in configuring the avro schema */
case class SchemaDecodeException(msg: String) extends RuntimeException(msg)
object SchemaDecodeException {
  def schemaDecodeException(msg: String) = throw SchemaDecodeException(msg)
}
