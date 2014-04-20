package nest.sparkle.loader.kafka

import scala.reflect.runtime.universe._
import scala.language.existentials
import org.apache.avro.generic.GenericRecord
import nest.sparkle.store.Event

/** metadata about a column e.g. from Avro array field */
case class NameAndType(val name: String, typed: TypeTag[_])

/** type and name information about a tabular array record (typically derived from an Avro schema) */
case class ArrayRecordMeta(
    val values: Seq[NameAndType],
    val key: NameAndType,
    val idType: TypeTag[_]) {
  def keyType: TypeTag[_] = ???
}

/** all values from an tabular array record, in columnar form */
case class ArrayRecordColumns(
    val id: Any,
    val columns: Seq[Seq[Event[_, _]]] // columns in the same order as in the values[NameAndType] sequence
    ) {
  
  /** combine type tags with data columns based on the metadata. */
  def typedColumns(metaData: ArrayRecordMeta): Seq[TaggedColumn] = {
    columns zip metaData.values map {
      case (column, NameAndType(name, typed)) =>
        TaggedColumn(name, keyType = metaData.key.typed, valueType = typed, column)
    }
  }

}

/** a single column of data, along with its name and type meta data */
case class TaggedColumn(name: String, keyType: TypeTag[_], valueType: TypeTag[_], events: Seq[Event[_, _]])

/** a decoder that transforms avro generic records to ArrayRecordColumns, bundled with the meta data
  * necessary to interpret the untyped ArrayRecordColumns
  */
case class ArrayRecordDecoder(val decodeRecord: GenericRecord => ArrayRecordColumns,
                              val metaData: ArrayRecordMeta)
