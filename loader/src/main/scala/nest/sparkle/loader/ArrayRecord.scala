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
package nest.sparkle.loader

import nest.sparkle.datastream.DataArray
import nest.sparkle.store.Event
import scala.language.existentials
import scala.reflect.runtime.universe._

/** metadata about a column e.g. from Avro array field
  * @param nullValue A value to use if the column is null or missing.
  */
case class NameTypeDefault(name: String, typed: TypeTag[_], nullValue: Option[_] = None)

/** type and name information about a tabular array record (typically derived from an Avro schema) */
case class ArrayRecordMeta(
  values: Seq[NameTypeDefault],
  key: NameTypeDefault,
  ids: Seq[NameTypeDefault])

/** all values from a tabular array record, organized in columns */
case class ArrayRecordColumns(
    ids: Seq[Option[Any]], // ids in the same order as in the ids[NameAndType] sequence    
    columns: Seq[Seq[Event[_, _]]] // columns in the same order as in the values[NameAndType] sequence
    ) {

  /** combine type tags with data columns based on the metadata. */
  def typedColumns(metaData: ArrayRecordMeta): Seq[TaggedColumn] = {
    columns zip metaData.values map {
      case (column, NameTypeDefault(name, typed, _)) =>
        TaggedColumn(name, keyType = metaData.key.typed, valueType = typed, column)
    }
  }
}

/** a chunk of column of data to load into the store, along with its name and type meta data */ // TODO get rid of this in favor of TaggedSlice
case class TaggedColumn(name: String, keyType: TypeTag[_], valueType: TypeTag[_], events: Seq[Event[_, _]])

/** a chunk of data (in DataArray format) to load into the store into one column */
case class TaggedSlice[T: TypeTag, U: TypeTag](columnPath: String, dataArray: DataArray[T, U]) {
  def keyType = implicitly[TypeTag[T]]
  def valueType = implicitly[TypeTag[U]]
  def shortPrint(maxEvents: Int): String = {
    val eventsString = dataArray.take(maxEvents).map { case (k, v) => s"($k, $v)" }.mkString(", ")
    s"columnPath:$columnPath  events: $eventsString"
  }
}

/** decoder that maps records of type R to ArrayRecordColumns... e.g. avro GenericRecord -> ArrayRecordColumns **/
case class ArrayRecordDecoder[R](decodeRecord: R => ArrayRecordColumns,
                                 metaData: ArrayRecordMeta)
