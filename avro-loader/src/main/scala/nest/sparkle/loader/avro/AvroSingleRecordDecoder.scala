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

import nest.sparkle.loader.{ArrayRecordDecoder, ArrayRecordColumns, ArrayRecordMeta, NameTypeDefault}
import nest.sparkle.store.Event
import nest.sparkle.util.Log
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.avro.util.Utf8

object AvroSingleRecordDecoder extends AvroDecoder with Log {

  def decoder(schema: Schema,  // format: OFF
              idFields: Seq[(String,Option[String])] = Seq(("id", None)),
              keyField: String = "time",
              skipValueFields: Set[String] = Set(),
              filterOpt:Option[ArrayRecordFilter] = None
               ): ArrayRecordDecoder[GenericRecord] = // format: ON
  {
    val metaData = {
      val idFieldNames = idFields.map{ case (idField, _) => idField }

      val ids: Seq[NameTypeDefault] = {
        val idTypes = typeTagFields(schema, idFieldNames)
        idFields zip idTypes map {
          case ((id, default), typed) => NameTypeDefault(id, typed, default)
        }
      }

      val key = {
        val keyType = typeTagField(schema, keyField)
        NameTypeDefault(keyField, keyType)
      }

      val values: Seq[NameTypeDefault] = {
        val valueFields = fieldsExcept(schema, (skipValueFields + keyField) ++ Set(idFieldNames:_*))
        val valueTypes = typeTagFields(schema, valueFields)
        valueFields zip valueTypes map {
          case (valueName, typed) =>
            NameTypeDefault(valueName, typed)
        }
      }
      ArrayRecordMeta(values = values, key = key, ids = ids)
    }

    val decoder = recordWithArrayDecoder(metaData, filterOpt)

    ArrayRecordDecoder[GenericRecord](decoder, metaData)
  }

  /** return a function that reads a Generic record into the ArrayRecordColumns format */
  private def recordWithArrayDecoder(sourceMetaData: ArrayRecordMeta,
                                     filterOpt: Option[ArrayRecordFilter]): GenericRecord => ArrayRecordColumns = {

    (record: GenericRecord) =>
      val ids = sourceMetaData.ids.map {
        case NameTypeDefault(name, _, _) => Option(record.get(name))
      }

      // collect all values in column format
      val valueColumns: Seq[Any] = {
        val valueNames = sourceMetaData.values.map { _.name }
        valueNames.map { name => record.get(name) }
      }

      val key = record.get(sourceMetaData.key.name)

      // convert to event column format
      val columns: Seq[Seq[Event[_, _]]] = {
        val k = key match {
          case s: Utf8 => s.toString
          case _       => key
        }

        val events = valueColumns.map { value =>
            val v = value match {
              case s: Utf8 => s.toString
              case _       => value
            }
            Event(k, v)
        }

        Seq(events)
      }

      ArrayRecordColumns(ids, columns)
  }
}
