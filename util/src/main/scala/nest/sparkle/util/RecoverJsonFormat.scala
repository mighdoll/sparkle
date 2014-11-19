package nest.sparkle.util

import nest.sparkle.util.OptionConversion._
import spray.json.{JsValue, JsonFormat}
import spray.json.DefaultJsonProtocol._

import scala.reflect.runtime.universe._
import scala.util.Try

case class JsonFormatNotFound(msg: String) extends RuntimeException(msg)

/** Dynamically get a JsonFormat instance from a TypeTag.  This enables generic
  * programming on numeric types where the specific numeric type is unknown
  * until the dynamic type is recovered. e.g. serialized data over the network
  * or in a database may record its type, but that type isn't known at compile time.
  * (The type is recovered at runtime during deserialization)
  */
object RecoverJsonFormat {
    /** mapping from typeTag to JsonFormat for standard types */
  val jsonFormats: Map[TypeTag[_], JsonFormat[_]] = Map(
      typeToFormat[Boolean],
      typeToFormat[Short],
      typeToFormat[Int],
      typeToFormat[Long],
      typeToFormat[Double],
      typeToFormat[Char],
      typeToFormat[String],
      typeToFormat[JsValue]
    )

  /** return a mapping from a typetag to an Ordering */
  private def typeToFormat[T: TypeTag: JsonFormat]: (TypeTag[T], JsonFormat[T]) = {
    typeTag[T] -> implicitly[JsonFormat[T]]
  }

  /** return a JsonFormat instance at runtime based a typeTag. */ // TODO get rid of this, or at least build on tryJsonFormat
  def jsonFormat[T](targetTag: TypeTag[_]) // format: OFF 
      : JsonFormat[T] = { // format: ON
    val untypedFormat = jsonFormats.get(targetTag).getOrElse {
      throw JsonFormatNotFound(targetTag.tpe.toString)
    }
    
    untypedFormat.asInstanceOf[JsonFormat[T]]
  }
  

  /** return a JsonFormat instance at runtime based a typeTag. */
  def tryJsonFormat[T](targetTag: TypeTag[_]) // format: OFF 
      : Try[JsonFormat[T]] = { // format: ON
    val untypedFormat:Try[JsonFormat[_]] = jsonFormats.get(targetTag).toTryOr(JsonFormatNotFound(targetTag.tpe.toString))
    untypedFormat.asInstanceOf[Try[JsonFormat[T]]]
  }
  
  
}