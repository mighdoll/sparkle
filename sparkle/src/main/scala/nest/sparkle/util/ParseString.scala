package nest.sparkle.util

import scala.reflect.runtime.universe._

/** typeclass to parse strings to a value. */
abstract class ParseStringTo[T: TypeTag] {
  /** parse a string to a value. Subclasses must throw and exception if parsing fails */
  def parse(s: String): T

  /** type of the parsed return value */
  def typed = typeTag[T]
  
  override def toString = s"ParseStringTo[${typed.toString}]"
}

/** implementation of ParseStringTo typeclass for some common types */
object ParseStringTo {
  case class BooleanParseException(string: String) extends RuntimeException(string)

  /** implementation of ParseStringTo typeclass for some common types */
  object Implicits {
    implicit object StringToLong extends ParseStringTo[Long] {
      override def parse(s: String): Long = java.lang.Long.parseLong(s)
    }

    implicit object StringToInt extends ParseStringTo[Int] {
      override def parse(s: String): Int = Integer.parseInt(s)
    }

    implicit object StringToDouble extends ParseStringTo[Double] {
      override def parse(s: String): Double = java.lang.Double.parseDouble(s)
    }

    implicit object StringToBoolean extends ParseStringTo[Boolean] {
      override def parse(s: String): Boolean = s match {
        case "true"  => true
        case "false" => false
        case "True"  => true
        case "False" => false
        case "TRUE"  => true
        case "FALSE" => false
        case _       => throw BooleanParseException(s)
      }
    }
  }

  object StringToString extends ParseStringTo[String] {
    def parse(s: String): String = s
  }

}
