package nest.sparkle.store.cassandra
import scala.reflect.runtime.universe._
import nest.sparkle.store.cassandra.serializers._

/** Dynamically get a CanSerialize instance from a TypeTag.  */
object RecoverCanSerialize {
  object Implicits {
    /** mapping from typeTag to CanSerialize for standard types */
    implicit val standardCanSerialize: Map[TypeTag[_], CanSerialize[_]] = Map(
      typeToCanSerialize[Double],
      typeToCanSerialize[Long],
      typeToCanSerialize[Int],
      typeToCanSerialize[Short]
    )
  }

  /** return a mapping from a typetag to an Ordering */
  private def typeToCanSerialize[T: TypeTag: CanSerialize]: (TypeTag[T], CanSerialize[T]) = {
    typeTag[T] -> implicitly[CanSerialize[T]]
  }

  /** return a CanSerialize instance at runtime based a typeTag. */
  def optCanSerialize[T](targetTag: TypeTag[_]) // format: OFF 
      (implicit canSerializers: Map[TypeTag[_], CanSerialize[_]] = Implicits.standardCanSerialize)
      : Option[CanSerialize[T]] = { // format: ON
    val untypedCanSerialize = canSerializers.get(targetTag)
    untypedCanSerialize.asInstanceOf[Option[CanSerialize[T]]]
  }

}