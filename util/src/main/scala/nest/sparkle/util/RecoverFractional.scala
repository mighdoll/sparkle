package nest.sparkle.util

import scala.reflect.runtime.universe._

case class FractionalNotFound(msg: String) extends RuntimeException(msg)

/** Dynamically get a Fractional instance from a TypeTag.  This enables generic
  * programming on numeric types where the specific numeric type is unknown
  * until the dynamic type is recovered. e.g. serialized data over the network
  * or in a database may record its type, but that type isn't known at compile time.
  * (The type is recovered at runtime during deserialization)
  */
object RecoverFractional {
  object Implicits {
    /** mapping from typeTag to Fractional for standard types */
    implicit val standardFractional: Map[TypeTag[_], Fractional[_]] = Map(
      typeToFractional[Double],
      typeToFractional[Float]
    )
  }

  /** return a mapping from a typetag to an Ordering */
  private def typeToFractional[T: TypeTag: Fractional]: (TypeTag[T], Fractional[T]) = {
    typeTag[T] -> implicitly[Fractional[T]]
  }

  /** return a Fractional instance at runtime based a typeTag. */
  def optFractional[T](targetTag: TypeTag[_]) // format: OFF 
      (implicit fractions: Map[TypeTag[_], Fractional[_]] = Implicits.standardFractional)
      : Option[Fractional[T]] = { // format: ON
    val untypedFractional = fractions.get(targetTag)
    untypedFractional.asInstanceOf[Option[Fractional[T]]]
  }
}