package nest.sparkle.store
import scala.reflect.runtime.universe._

/** Extractor that matches the dynamic type of a column and returns a provided static type if it matches */
abstract class TypedColumn[T: TypeTag, U: TypeTag] {
  def unapply(column: Column[_, _]): Option[Column[T, U]] = {
    val argumentTag = typeTag[T]
    val valueTag = typeTag[U]

    (column.keyType.tpe, column.valueType.tpe) match {
      case (argumentTag, valueTag) =>
        val castColumn = column.asInstanceOf[Column[T, U]]
        Some(castColumn)
      case _ => None
    }
  }
}

object LongDoubleColumn extends TypedColumn[Long, Double]
object LongLongColumn extends TypedColumn[Long, Long]

