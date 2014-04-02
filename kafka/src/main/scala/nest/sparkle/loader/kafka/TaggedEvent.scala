package nest.sparkle.loader.kafka

import nest.sparkle.store.Event
import scala.reflect.runtime.universe._
import scala.language.existentials
import nest.sparkle.util.Exceptions.NYI

/** an untyped event combined with some type tags that describe the type of its arguments and values */
case class TaggedEvent(event: Event[_, _], keyType: TypeTag[_], valueType: TypeTag[_])

/** Extractor for TaggedEvent, subclasses will return a specific static type
  * if they match.
  */
abstract class TaggedEventExtractor[T: TypeTag, U: TypeTag] {
  def unapply(taggedEvent: TaggedEvent): Option[Event[T, U]] = {
    val expectA = typeTag[T].tpe
    val expectB = typeTag[U].tpe

    if (taggedEvent.keyType.tpe <:< expectA && taggedEvent.valueType.tpe <:< expectB) {
      Some(taggedEvent.event.asInstanceOf[Event[T, U]])
    } else {
      None
    }
  }
}

case object LongDoubleTaggedEvent extends TaggedEventExtractor[Long, Double]
case object LongLongTaggedEvent extends TaggedEventExtractor[Long, Long]
case object TypedStringDouble extends TaggedEventExtractor[String, Double]

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