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

import scala.language.existentials
import scala.reflect.runtime.universe._
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

import org.apache.avro.generic.GenericRecord
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData

import nest.sparkle.loader.kafka.SchemaDecodeException.schemaDecodeException
import nest.sparkle.store.Event
import nest.sparkle.util.Log

/** Utility for making the Decoder part of an AvroColumnDecoder from an avro schema when
  * the avro schema contains an array of key,values fields.
  */
object AvroArrayDecoder extends Log {
  /** Type of a filter that can process input data as it arrives */
  type ArrayRecordFilter = (ArrayRecordMeta, Seq[Seq[Event[_, _]]]) => Seq[Seq[Event[_, _]]]

  /** Return a decoder for an avro schema containing one or more ids and an array of key,[value,..] records.
    * There can be more than one value in each row of the array.  The value fields are copied
    * from the avro schema field names.
    *
    * idFields names will be joined and separated by slashes to form the column path.
    * An optional default value for an id field may be specified if an avro record
    * is missing the field or it is NULL.
    *
    * See `MillisDoubleArrayAvro` in the tests for an example avro schema in the expected structure.
    *
    * @param idFields List of field names which will be separated by slashes to
    *               form the column name
    * @param skipValueFields a blacklist of values fields in each array row to ignore
    */
  def decoder(schema: Schema,  // format: OFF 
              arrayField: String = "elements",
              idFields: Seq[(String,Option[String])] = Seq(("id", None)),
              keyField: String = "time",
              skipValueFields: Set[String] = Set(), 
              filterOpt:Option[ArrayRecordFilter] = None
              ): ArrayRecordDecoder = // format: ON
    {
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
        val ids: Seq[NameTypeDefault] = {
          val fieldNames = idFields.map{ case (idField, _) => idField }
          val idTypes = typeTagFields(schema, fieldNames)
          idFields zip idTypes map {
            case ((id, default), typed) => NameTypeDefault(id, typed, default)
          }
        }

        val key = {
          val keyType = typeTagField(elementSchema, keyField)
          NameTypeDefault(keyField, keyType)
        }

        val values: Seq[NameTypeDefault] = {
          val valueFields = fieldsExcept(elementSchema, skipValueFields + keyField)
          val valueTypes = typeTagFields(elementSchema, valueFields)
          valueFields zip valueTypes map {
            case (valueName, typed) =>
              NameTypeDefault(valueName, typed)
          }
        }
        ArrayRecordMeta(values = values, key = key, ids = ids)
      }

      val decoder = recordWithArrayDecoder(arrayField, metaData, filterOpt)

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
  private def unionTypeTag(schema: Schema): TypeTag[_] = {
    val schemas = schema.getTypes.toSeq
    if (schemas.size != 2) {
      schemaDecodeException("avro field is a union of more than 2 types")
    }
    val types = schemas.map(_.getType)
    if (!types.contains(Schema.Type.NULL)) {
      schemaDecodeException("avro field is a union with out a null")
    }

    // Find the first supported type, really should only be skipping NULL.
    types.collectFirst({
      case Schema.Type.LONG    => typeTag[Long]
      case Schema.Type.INT     => typeTag[Int]
      case Schema.Type.DOUBLE  => typeTag[Double]
      case Schema.Type.BOOLEAN => typeTag[Boolean]
      case Schema.Type.STRING  => typeTag[String]
      case Schema.Type.FLOAT   => typeTag[Float]
    }).getOrElse(schemaDecodeException("avro field is a union with out a supported type"))
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
  private def recordWithArrayDecoder(arrayField: String,
    sourceMetaData: ArrayRecordMeta, filterOpt: Option[ArrayRecordFilter]): GenericRecord => ArrayRecordColumns = {

    (record: GenericRecord) =>
      val ids = sourceMetaData.ids.map {
        case NameTypeDefault(name, _, _) => Option(record.get(name))
      }

      val array = record.get(arrayField).asInstanceOf[GenericData.Array[GenericData.Record]]
      log.debug(s"reading record containing ${array.size} elements")

      /** map over all the rows in the array embedded in the record */
      def mapRows[T](fn: GenericData.Record => T): Seq[T] = {
        (0 until array.size).map { rowDex =>
          fn(array.get(rowDex))
        }
      }

      // collect all values in column format
      val valueColumns: Seq[Seq[Any]] = {
        val valueNames = sourceMetaData.values.map { _.name }
        valueNames.map { name =>
          mapRows { row =>
            row.get(name)
          }
        }
      }

      // single column of just the keys
      val keys: Seq[Any] = {
        mapRows { row =>
          row.get(sourceMetaData.key.name)
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

      ArrayRecordColumns(ids, columns)
  }
}

/** failure in configuring the avro schema */
case class SchemaDecodeException(msg: String) extends RuntimeException(msg)
object SchemaDecodeException {
  def schemaDecodeException(msg: String): Nothing = throw SchemaDecodeException(msg)
}
