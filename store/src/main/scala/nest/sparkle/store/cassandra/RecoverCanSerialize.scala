package nest.sparkle.store.cassandra

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.reflect.runtime.universe._
import scala.util.Try
import java.nio.ByteBuffer

import com.typesafe.config.Config
import spray.json.JsValue

import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.{Instance, GenericFlags}
import nest.sparkle.util.OptionConversion._

case class CanSerializeNotFound(msg: String) extends RuntimeException(msg)

/** Dynamically get a CanSerialize instance from a TypeTag.  */
class RecoverCanSerialize(sparkleConfig: Config) {

  private val basicCanSerializers: Map[TypeTag[_], CanSerialize[_]] = Map(
    typeToCanSerialize[Boolean],
    typeToCanSerialize[Short],
    typeToCanSerialize[Int],
    typeToCanSerialize[Long],
    typeToCanSerialize[Double],
    typeToCanSerialize[Char],
    typeToCanSerialize[String],
    typeToCanSerialize[JsValue],
    typeToCanSerialize[GenericFlags],
    typeToCanSerialize[ByteBuffer]
  )

  /** mapping from typeTag to CanSerialize for standard types */
  val canSerializers = makeCanSerializers

  /** return a CanSerialize instance at runtime based a typeTag. */
  def optCanSerialize[T](targetTag: TypeTag[_]) // format: OFF 
      : Option[CanSerialize[T]] = { // format: ON
    val untypedCanSerialize = canSerializers.get(targetTag)
    untypedCanSerialize.asInstanceOf[Option[CanSerialize[T]]]
  }

  /** return a CanSerialize instance at runtime based a typeTag. */
  def tryCanSerialize[T](implicit targetTag: TypeTag[_]): Try[CanSerialize[T]] = {
    val untyped: Try[CanSerialize[_]] = canSerializers.get(targetTag).toTryOr(
      CanSerializeNotFound(targetTag.tpe.toString))
    untyped.asInstanceOf[Try[CanSerialize[T]]]

  }

  /** return a mapping from a typetag to a can Serialize */
  private def typeToCanSerialize[T: TypeTag: CanSerialize]: (TypeTag[T], CanSerialize[T]) = {
    typeTag[T] -> implicitly[CanSerialize[T]]
  }

  private def makeCanSerializers: Map[TypeTag[_], CanSerialize[_]] = {
    CanSerializeConfigUtil.makeWithConfig(sparkleConfig, basicCanSerializers) { (typeTagClassName: String, canSerializerClassName: String) =>
      def withFixedType[U]() = {
        val typeTag = Instance.typeTagByClassName[U](typeTagClassName)
        val canSerializer = Instance.objectByClassName[CanSerialize[U]](canSerializerClassName)
        typeToCanSerialize(typeTag, canSerializer)
      }
      withFixedType()
    }
  }
}

object CanSerializeConfigUtil {
  def makeWithConfig[X, Y](sparkleConfig: Config, basic: Map[X, Y])
      (fn: (String, String) => (X, Y)): Map[X, Y] = {
    makeWithConfig(sparkleConfig, basic.toSeq)(fn).toMap
  }

  def makeWithConfig[X](sparkleConfig: Config, basic: Seq[X])
      (fn: (String, String) => X): Seq[X] = {
    if (sparkleConfig.hasPath("sparkle-store-cassandra.serializers")) {
      sparkleConfig.getConfigList("sparkle-store-cassandra.serializers").asScala.toSeq.map { serializerConfig =>
        fn(serializerConfig.getString("type"), serializerConfig.getString("type-serializer"))
      } ++ basic
    } else {
      basic
    }
  }
}
