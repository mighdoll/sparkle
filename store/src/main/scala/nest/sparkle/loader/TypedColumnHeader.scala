package nest.sparkle.loader

import nest.sparkle.util.StringUtil
import scala.reflect.runtime.universe._
import spray.json.JsValue

/** support for parsing text column headers with ascribed types e.g. myColumn:Int */
object TypedColumnHeader {
  /** pattern match support for identifying the ascribed type of a column */
  def unapply(column: String): Option[TypeTag[_]] = {
    suffix(column) match {
      case Some("boolean") => Some(typeTag[Boolean])
      case Some("bool")    => Some(typeTag[Boolean])
//      case Some("short")   => Some(typeTag[Short]) // TODO fixme, breaks other tests when enabled
      case Some("int")     => Some(typeTag[Int])
      case Some("long")    => Some(typeTag[Long])
      case Some("double")  => Some(typeTag[Double])
//      case Some("char")    => Some(typeTag[Char]) // TODO fixme, breaks other tests when enabled
      case Some("string")  => Some(typeTag[String])
      case Some("json")    => Some(typeTag[JsValue])
      case _               => None
    }
  }

  /** return the column header without the type ascription e.g. "myColumn:Int" => "myColumn" */
  def withoutSuffix(column: String): String = {
    suffix(column) match {
      case Some(suffix) =>
        val suffixLength = ":".length + suffix.length
        column.take(column.length - suffixLength)
      case _ =>
        column
    }
  }

  /** return the :suffix of a string "myColumn:Int" => "Int" */
  private def suffix(column: String): Option[String] = {
    StringUtil.findSuffix(column.toLowerCase, ":")
  }
}