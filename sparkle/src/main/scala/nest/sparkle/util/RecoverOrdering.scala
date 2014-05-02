package nest.sparkle.util
import scala.reflect.runtime.universe._

case class OrderingNotFound(msg: String) extends RuntimeException(msg)

/** @define ordering Recover a typed Ordering instance dynamically from a TypeTag.
  * @define _end
  *
  * $ordering
  */
object RecoverOrdering {
  object Implicits {
    /** mapping from typeTag to Ordering for standard types */
    implicit val standardOrderings: Map[TypeTag[_], Ordering[_]] = Map(
      typeToOrdering[Double],
      typeToOrdering[Long],
      typeToOrdering[Int],
      typeToOrdering[Short],
      typeToOrdering[Char],
      typeToOrdering[String]
    )
  }

  /** return a mapping from a typetag to an Ordering */
  private def typeToOrdering[T: TypeTag: Ordering]: (TypeTag[T], Ordering[T]) = {
    typeTag[T] -> Ordering[T]
  }

  /** $ordering
    *
    * The types that can be converted to Orderings are specified by the implicit parameter @param orderings.
    * A standard set of Ordering conversions for built in types is in Implicits.standardOrderings.
    *
    * Throws OrderingNotFound if no Ordering is available
    */
  def ordering[T](targetTag: TypeTag[_]) // format:OFF 
      (implicit orderings: Map[TypeTag[_], Ordering[_]] = Implicits.standardOrderings)
      : Ordering[T] = { // format: ON

    val untypedOrdering =
      Implicits.standardOrderings.get(targetTag).getOrElse {
        throw OrderingNotFound(targetTag.tpe.toString)
      }
    untypedOrdering.asInstanceOf[Ordering[T]]
  }
}

