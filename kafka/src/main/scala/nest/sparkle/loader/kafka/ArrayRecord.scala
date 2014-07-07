package nest.sparkle.loader.kafka

import scala.reflect.runtime.universe._
import scala.language.existentials

import org.apache.avro.generic.GenericRecord

import nest.sparkle.store.Event

/** metadata about a column e.g. from Avro array field 
  * @param nullValue A value to use if the column is null or missing.
  */
case class NameAndType(name: String, typed: TypeTag[_], nullValue: Option[_] = None)

/** type and name information about a tabular array record (typically derived from an Avro schema) */
case class ArrayRecordMeta(
    values: Seq[NameAndType],
    key: NameAndType,
    ids: Seq[NameAndType]) {
  def keyType: TypeTag[_] = ???
}

/** all values from a tabular array record, organized in columns */
case class ArrayRecordColumns(
    ids: Seq[Option[Any]],
    columns: Seq[Seq[Event[_, _]]] // columns in the same order as in the values[NameAndType] sequence
    ) {

  /** combine type tags with data columns based on the metadata. */
  def typedColumns(metaData: ArrayRecordMeta): Seq[TaggedColumn] = {
    columns zip metaData.values map {
      case (column, NameAndType(name, typed, _)) =>
        TaggedColumn(name, keyType = metaData.key.typed, valueType = typed, column)
    }
  }

}

/** a single column of data, along with its name and type meta data */
case class TaggedColumn(name: String, keyType: TypeTag[_], valueType: TypeTag[_], events: Seq[Event[_, _]])

/** a decoder that transforms avro generic records to ArrayRecordColumns, bundled with the meta data
  * necessary to interpret the untyped ArrayRecordColumns
  */
case class ArrayRecordDecoder(decodeRecord: GenericRecord => ArrayRecordColumns,
                              metaData: ArrayRecordMeta)
