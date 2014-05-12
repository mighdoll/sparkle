package nest.sparkle.util
import scala.reflect.runtime.universe._
import spire.math.Numeric

case class NumericNotFound(msg: String) extends RuntimeException(msg)

/** Dynamically get a Numeric instance from a TypeTag.  This enables generic
  * programming on numeric types where the specific numeric type is unknown
  * until the dynamic type is recovered. e.g. serialized data over the network
  * or in a database may record its type, but that type isn't known at compile time.
  * (The type is recovered at runtime during deserialization)
  */
object RecoverNumeric {
  object Implicits {
    /** mapping from typeTag to Numeric for standard types */
    implicit val standardNumeric: Map[TypeTag[_], Numeric[_]] = Map(
      typeToNumeric[Double],
      typeToNumeric[Long],
      typeToNumeric[Int],
      typeToNumeric[Short]
    )
  }

  /** return a mapping from a typetag to an Ordering */
  private def typeToNumeric[T: TypeTag: Numeric]: (TypeTag[T], Numeric[T]) = {
    typeTag[T] -> implicitly[Numeric[T]]
  }

  /** return a Numeric instance at runtime based a typeTag. */
  def optNumeric[T](targetTag: TypeTag[_]) // format: OFF 
      (implicit numerics: Map[TypeTag[_], Numeric[_]] = Implicits.standardNumeric)
      : Option[Numeric[T]] = { // format: ON
    val untypedNumeric = numerics.get(targetTag)
    untypedNumeric.asInstanceOf[Option[Numeric[T]]]
  }
}