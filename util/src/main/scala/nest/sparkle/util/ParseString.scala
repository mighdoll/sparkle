package nest.sparkle.util

import java.nio.ByteBuffer

import com.google.common.base.Charsets
import com.google.common.io.BaseEncoding
import spray.json.JsValue

import scala.reflect.runtime.universe._
import nest.sparkle.util.KindCast.castKind

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

    implicit object StringToChar extends ParseStringTo[Char] {
      override def parse(s: String): Char = s(0)
    }

    implicit object StringToJson extends ParseStringTo[JsValue] {
      import spray.json._
      override def parse(s: String): JsValue = s.asJson
    }

    implicit object StringToShort extends ParseStringTo[Short] {
      override def parse(s: String): Short = {
        println(s"StringToShort: $s")
        java.lang.Short.parseShort(s)
      }
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

    implicit object StringToString extends ParseStringTo[String] {
      def parse(s: String): String = s
    }

    implicit object StringToGenericFlags extends ParseStringTo[GenericFlags] {
      override def parse(s: String): GenericFlags = GenericFlags(java.lang.Long.parseLong(s))
    }

    implicit object StringToBlob extends ParseStringTo[ByteBuffer] {
      def parse(s: String): ByteBuffer = ByteBuffer.wrap(BaseEncoding.base64().decode(s))
    }
  }

  /** optionally return a string parser for a given type. If no std parser is available, returns None. */
  def findParser[T: TypeTag]: Option[ParseStringTo[T]] = {
    import nest.sparkle.util.ParseStringTo.Implicits._
    val existential: Option[ParseStringTo[_]] =
      typeTag[T].tpe match {
        case t if t <:< typeOf[Boolean]      => Some(StringToBoolean)
        case t if t <:< typeOf[Short]        => Some(StringToShort)
        case t if t <:< typeOf[Int]          => Some(StringToInt)
        case t if t <:< typeOf[Long]         => Some(StringToLong)
        case t if t <:< typeOf[Double]       => Some(StringToDouble)
        case t if t <:< typeOf[Char]         => Some(StringToChar)
        case t if t <:< typeOf[String]       => Some(StringToString)
        case t if t <:< typeOf[JsValue]      => Some(StringToJson)
        case t if t <:< typeOf[GenericFlags] => Some(StringToGenericFlags)
        case t if t <:< typeOf[ByteBuffer]   => Some(StringToBlob)
      }

    // convert to the appropriate type
    val typed: Option[ParseStringTo[T]] = existential.map { castKind(_) }
    typed
  }

}
