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

package nest.sparkle.loader.avro

import nest.sparkle.loader.{ArrayRecordDecoder, NameTypeDefault, ArrayRecordColumns, ArrayRecordMeta}
import nest.sparkle.store.Event
import nest.sparkle.util.Log
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.util.Utf8

import scala.language.existentials

/** Utility for making the Decoder part of an AvroColumnDecoder from an avro schema when
  * the avro schema contains an array of key,values fields.
  */
object AvroArrayDecoder extends AvroDecoder with Log {

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
    * @param valueFieldsFilter either a whitelist of values fields in each array row to consider, or
    *               a blacklist of values fields in each array row to ignore, determined by
    *               valueFieldsFilterIsBlackList param
    * @param valueFieldsFilterIsBlackList whether valueFieldsFilter represents a whitelist or blacklist
    */
  def decoder(schema: Schema,  // format: OFF
              arrayField: String = "elements",
              idFields: Seq[(String,Option[String])] = Seq(("id", None)),
              keyField: String = "time",
              valueFieldsFilter: Set[String] = Set(),
              valueFieldsFilterIsBlackList: Boolean = true,
              filterOpt:Option[ArrayRecordFilter] = None ): ArrayRecordDecoder[GenericRecord] = // format: ON
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
        SchemaDecodeException.schemaDecodeException(s"$arrayField is not extractable from schema $name")
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
          val valueFields = {
            if (valueFieldsFilterIsBlackList) fieldsExcept(elementSchema, valueFieldsFilter + keyField)
            else fields(elementSchema, valueFieldsFilter)
          }
          val valueTypes = typeTagFields(elementSchema, valueFields)
          valueFields zip valueTypes map {
            case (valueName, typed) =>
              NameTypeDefault(valueName, typed)
          }
        }
        ArrayRecordMeta(values = values, key = key, ids = ids)
      }

      val decoder = recordWithArrayDecoder(arrayField, metaData, filterOpt)

      ArrayRecordDecoder[GenericRecord](decoder, metaData)
    }

  /** return a function that reads a Generic record into the ArrayRecordColumns format */
  private def recordWithArrayDecoder(arrayField: String,
    sourceMetaData: ArrayRecordMeta, filterOpt: Option[ArrayRecordFilter]): GenericRecord => ArrayRecordColumns = {

    (record: GenericRecord) =>
      val ids = sourceMetaData.ids.map {
        case NameTypeDefault(name, _, _) => Option(record.get(name))
      }

      val array = record.get(arrayField).asInstanceOf[GenericData.Array[GenericData.Record]]
      log.trace(s"reading record containing ${array.size} elements")

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
              val k = key match {
                case s: Utf8 => s.toString
                case _       => key
              }
              val v = value match {
                case s: Utf8 => s.toString
                case _       => value
              }
              Event(k, v)
          }
        }
      }

      ArrayRecordColumns(ids, columns)
  }
}
