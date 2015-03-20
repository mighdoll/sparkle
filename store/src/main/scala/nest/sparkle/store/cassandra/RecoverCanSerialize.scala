package nest.sparkle.store.cassandra

import scala.reflect.runtime.universe._
import nest.sparkle.store.cassandra.serializers._
import spray.json.JsValue
import scala.util.Try
import nest.sparkle.util.GenericFlags
import nest.sparkle.util.OptionConversion._

case class CanSerializeNotFound(msg: String) extends RuntimeException(msg)

/** Dynamically get a CanSerialize instance from a TypeTag.  */
object RecoverCanSerialize {
  /** mapping from typeTag to CanSerialize for standard types */
  val canSerializers: Map[TypeTag[_], CanSerialize[_]] = Map(
    typeToCanSerialize[Boolean],
    typeToCanSerialize[Short],
    typeToCanSerialize[Int],
    typeToCanSerialize[Long],
    typeToCanSerialize[Double],
    typeToCanSerialize[Char],
    typeToCanSerialize[String],
    typeToCanSerialize[JsValue],
    typeToCanSerialize[GenericFlags]
  )

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

}