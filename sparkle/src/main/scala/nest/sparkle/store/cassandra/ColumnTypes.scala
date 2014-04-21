package nest.sparkle.store.cassandra

import scala.reflect.runtime.universe._

import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.TaggedKeyValueExtractor

object ColumnTypes {
  /** all columns types supported by this */
  val supportedColumnTypes: Seq[SerializeInfo[_, _]] = List(
    createSerializationInfo[Long, Long](),
    createSerializationInfo[Long, Double](),
    createSerializationInfo[Long, Int](),
    createSerializationInfo[Long, Boolean](),
    createSerializationInfo[Long, String]()
  )

  /** retrieve the serialization info for one of the supported column types */
  def serializationInfo[T: CanSerialize, U: CanSerialize](): SerializeInfo[T, U] = {
    val domainSerializer = implicitly[CanSerialize[T]]
    val rangeSerializer = implicitly[CanSerialize[U]]

    val found = supportedColumnTypes.find{ info =>
      info.domain == domainSerializer && info.range == rangeSerializer
    }
    found.get.asInstanceOf[SerializeInfo[T, U]]
  }
  
  /** return some serialization info for the types provided */
  private def createSerializationInfo[T: CanSerialize, U: CanSerialize](): SerializeInfo[T, U] = {
    val domainSerializer = implicitly[CanSerialize[T]]
    val rangeSerializer = implicitly[CanSerialize[U]]

    // cassandra storage types for the argument and value
    val argumentStoreType = domainSerializer.columnType
    val valueStoreType = rangeSerializer.columnType
    val tableName = argumentStoreType + "0" + valueStoreType

    SerializeInfo(domainSerializer, rangeSerializer, tableName)
  }

  /** holder for serialization info for given domain and range types */
  case class SerializeInfo[T, U](domain: CanSerialize[T], range: CanSerialize[U], tableName: String)
}


/** extractors for type pairs of supported column types */
object LongDoubleSerializers extends SerializerExtractor[Long, Double] 
object LongLongSerializers extends SerializerExtractor[Long, Long]
object LongIntSerializers extends SerializerExtractor[Long, Int]
object LongBooleanSerializers extends SerializerExtractor[Long, Boolean]
object LongStringSerializers extends SerializerExtractor[Long, String]

/** a pair of cassandra serializers for key and value */ 
case class KeyValueSerializers[T, U](keySerializer: CanSerialize[T], valueSerializer: CanSerialize[U])

/** support for writing an extractor from TaggedKeyValue to a KeyValueSerializer */
class SerializerExtractor[T: CanSerialize :TypeTag, U: CanSerialize :TypeTag] 
    extends TaggedKeyValueExtractor[T, U, KeyValueSerializers[T, U]] {
  def cast = KeyValueSerializers(implicitly[CanSerialize[T]], implicitly[CanSerialize[U]])
}
