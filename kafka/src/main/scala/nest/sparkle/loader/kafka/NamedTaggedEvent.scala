package nest.sparkle.loader.kafka

import scala.language.existentials
import scala.reflect.runtime.universe._

import org.apache.avro.util.Utf8

import nest.sparkle.store.Event
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
case object LongBooleanEvents extends TaggedEventExtractor[Long, Boolean]
case object LongIntEvents extends TaggedEventExtractor[Long, Int]
case object LongStringEvents extends TaggedEventExtractor[Long, String]
case object StringDoubleEvents extends TaggedEventExtractor[String, Double]

/** utilities for working with type tags for the kafka loader */
object TypeTagUtil {

  /** convert a value to a string based on a provided TypeTag */
  def typeTaggedToString(value: Any, typed: TypeTag[_]): String = {
    value match {
      case s: String if typed.tpe <:< typeOf[String]   => s
      case s: Utf8 if typed.tpe <:< typeOf[String]     => s.toString
      case l: Long if typed.tpe <:< typeOf[Long]       => l.toString
      case i: Int if typed.tpe <:< typeOf[Int]         => i.toString
      case d: Double if typed.tpe <:< typeOf[Double]   => d.toString
      case b: Boolean if typed.tpe <:< typeOf[Boolean] => b.toString
      case _                                           => NYI(s"support for string converting value $value of type: $typed")
    }
  }

}
