package nest.sparkle.store.cassandra

import scala.reflect.runtime.universe._
import java.nio.ByteBuffer

import com.typesafe.config.Config
import spray.json.JsValue

import nest.sparkle.store.cassandra.ColumnTypes._
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.Exceptions._
import nest.sparkle.util.{GenericFlags, Instance, Log}

object ColumnTypes {

  // Cassandra table name length limit per http://cassandra.apache.org/doc/cql3/CQL.html#createTableStmt
  private final val maxTableNameLength = 32

  private[cassandra] def validateTableName(tableName: String): Boolean = {
    !tableName.isEmpty && (tableName.length <= maxTableNameLength) && tableName.forall(_.isLetterOrDigit)
  }

  case class UnsupportedColumnType[T, U](keySerial: CanSerialize[T], valueSerial: CanSerialize[U])
    extends RuntimeException(s"${keySerial.columnType}-${valueSerial.columnType}")

  /** holder for serialization info for given key and value types */
  case class SerializeInfo[T, U](keySerializer: CanSerialize[T],
    valueSerializer: CanSerialize[U],
    tableName: String,
    directToNative: Boolean)
}

class ColumnTypes(sparkleConfig: Config) extends Log {

  private val basicTypeTags: Map[String, TypeTag[_]] = Map(
    "Boolean"                        -> typeTag[Boolean],
    "Short"                          -> typeTag[Short],
    "Int"                            -> typeTag[Int],
    "Long"                           -> typeTag[Long],
    "Double"                         -> typeTag[Double],
    "Char"                           -> typeTag[Char],
    "String"                         -> typeTag[String],
    "spray.json.JsValue"             -> typeTag[JsValue],
    "nest.sparkle.util.GenericFlags" -> typeTag[GenericFlags],
    "java.nio.ByteBuffer"            -> typeTag[ByteBuffer]
  )

  private val basicSupportedColumnTypes: Seq[SerializeInfo[_, _]] = List(
    createSerializationInfo[Long, Boolean](),
    createSerializationInfo[Long, Short](false),
    createSerializationInfo[Long, Int](),
    createSerializationInfo[Long, Long](),
    createSerializationInfo[Long, Double](),
    createSerializationInfo[Long, Char](false),
    createSerializationInfo[Long, String](),
    createSerializationInfo[Long, JsValue](false),
    createSerializationInfo[Long, GenericFlags](false),
    createSerializationInfo[Long, ByteBuffer]()
  )

  /** mapping from cassandra serialized nativeType string to typeTag */
  val typeTags = makeTypeTags

  /** the column types in the store are currently fixed, so that we can prepare CQL statement
    * variants for each type of column
    */
  val supportedColumnTypes = makeSupportedColumnTypes

  /** return a TypeTag from cassandra serialized nativeType string */
  def stringToTypeTag(typeString: String): TypeTag[_] = {
    typeTags.getOrElse(typeString, NYI(s"unsupported storage type $typeString"))
  }

  /** return a TypeTag from cassandra serialized nativeType string */
  def stringToCanSerialize(typeString: String): TypeTag[_] = {
    typeTags.getOrElse(typeString, NYI(s"unsupported storage type $typeString"))
  }

  /** retrieve the serialization info for one of the supported column types */
  def serializationInfo[T: CanSerialize, U: CanSerialize](): SerializeInfo[T, U] = {
    val keySerialize = implicitly[CanSerialize[T]]
    val valueSerialize = implicitly[CanSerialize[U]]

    val found = supportedColumnTypes.find{ info =>
      info.keySerializer == keySerialize && info.valueSerializer == valueSerialize
    }.getOrElse {
      val err = UnsupportedColumnType(keySerialize, valueSerialize)
      log.error(s"serializationInfo not found", err)
    }

    found.asInstanceOf[SerializeInfo[T, U]]
  }

  /** return some serialization info for the types provided */
  private def createSerializationInfo[T: CanSerialize, U: CanSerialize] // format: OFF
      (directToNative: Boolean = true)
      : SerializeInfo[T, U] = { // format: ON
    val keySerializer = implicitly[CanSerialize[T]]
    val valueSerializer = implicitly[CanSerialize[U]]

    // cassandra storage types for the key and value
    val keyStoreType = keySerializer.columnType
    val valueStoreType = valueSerializer.columnType
    val tableName = keyStoreType + "0" + valueStoreType

    assert(ColumnTypes.validateTableName(tableName), s"invalid table name $tableName")

    SerializeInfo(keySerializer, valueSerializer, tableName, directToNative)
  }

  private def makeTypeTags: Map[String, TypeTag[_]] = {
    CanSerializeConfigUtil.makeWithConfig(sparkleConfig, basicTypeTags) { (typeTagClassName: String, canSerializerClassName: String) =>
      def withFixedType[U]() = {
        val typeTag = Instance.typeTagByClassName[U](typeTagClassName)
        typeTagClassName -> typeTag
      }
      withFixedType()
    }
  }

  private def makeSupportedColumnTypes: Seq[SerializeInfo[_, _]] = {
    CanSerializeConfigUtil.makeWithConfig(sparkleConfig, basicSupportedColumnTypes) { (typeTagClassName: String, canSerializerClassName: String) =>
      def withFixedType[U]() = {
        val canSerializer =
          Instance.objectByClassName[CanSerialize[CanSerialize[U]]](canSerializerClassName)
        createSerializationInfo(false)(LongSerializer, canSerializer)
      }
      withFixedType()
    }
  }
}
