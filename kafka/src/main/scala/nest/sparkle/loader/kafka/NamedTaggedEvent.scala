package nest.sparkle.loader.kafka

import nest.sparkle.store.Event
import scala.reflect.runtime.universe._
import scala.language.existentials
import nest.sparkle.util.Exceptions.NYI

/** Extractor for TaggedColumn, subclasses will return a specific static type
  * if they match.
  */
abstract class TaggedEventExtractor[T: TypeTag, U: TypeTag] {
  def unapply(taggedEvent: TaggedColumn): Option[Seq[Event[T, U]]] = {
    val expectA = typeTag[T].tpe
    val expectB = typeTag[U].tpe

    if (taggedEvent.keyType.tpe <:< expectA && taggedEvent.valueType.tpe <:< expectB) {
      val events = taggedEvent.events.asInstanceOf[Seq[Event[T, U]]]
      Some(events)
    } else {
      None
    }
  }
}

case object LongDoubleEvents extends TaggedEventExtractor[Long, Double]
case object LongLongEvents extends TaggedEventExtractor[Long, Long]
case object LongIntEvents extends TaggedEventExtractor[Long, Int]
case object LongStringEvents extends TaggedEventExtractor[Long, String]
case object StringDoubleEvents extends TaggedEventExtractor[String, Double]

/** utilities for working with type tags for the kafka loader */
object TypeTagUtil {

  /** convert a value to a string based on a provided TypeTag */
  def typeTaggedToString(value: Any, typed: TypeTag[_]): String = {
    typed match {
      case t if t.tpe <:< typeOf[String] => value.toString
      case t if t.tpe <:< typeOf[Long]   => value.toString
      case t if t.tpe <:< typeOf[Int]    => value.toString
      case t if t.tpe <:< typeOf[Double] => value.toString
      case _                             => NYI(s"support for string converting value $value of type: $typed")
    }
  }

}
