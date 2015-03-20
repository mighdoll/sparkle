package nest.sparkle.util

import scala.collection.immutable

/**
 * For storing a set of optional flags as a Long.
 *
 * Uses two bits per field: the lower order bit indicates whether the field is
 * present, the higher order bit indicates whether the field is true or false.
 *
 * Within the Long, the lower order bit for a field is at: field position * 2,
 * and the higher order bit is at: field position * 2 + 1, where field position
 * is determined by the Flags' FieldsDescriptor.
 */
trait Flags extends Any {

  def MaxNumFlags = 32

  def fields: FieldsDescriptor

  def value: Long

  /** If the field at the specified position is present, returns whether the
    * field is true or false. */
  def fieldValue(position: Int): Option[Boolean] = {
    require(position < MaxNumFlags)
    if (bit(position * 2)) {
      Some(bit(position * 2 + 1))
    } else {
      None
    }
  }

  /** Returns a Flags object with this Flags' value and the field at the
    * specified position updated to the specified flag. */
  def updatedFlags(position: Int, flag: Boolean): Flags

  /** Returns a Long with this Flags' value and the field at the specified
    * position updated to the specified flag. Helper for updatedFlags(). */
  protected def updatedValue(position: Int, flag: Boolean): Long = {
    require(position < MaxNumFlags)
    var x = value | (1 << (position * 2)) // set bit to indicate field is present
    if (flag) x = x | (1 << (position * 2 + 1)) // set bit to indicate field is true
    x
  }

  /** Returns true if the specified bit is set in this Flags' value, false
    * otherwise. */
  private def bit(x: Int): Boolean = {
    ((value >> x) & 1) == 1
  }
}

/** Provides a mapping of field names to field positions for a Flags class. */
trait FieldsDescriptor {

  /** A map of field name to field position. */
  def fieldPositions: Map[String, Int]

  /** Returns the position of a field within a Flag. */
  def positionOfField(name: String): Option[Int] = {
    fieldPositions.get(name)
  }
}

/** A Flags class without any field name to field position mappings. */
case class GenericFlags(value: Long) extends AnyVal with Flags {

  override def fields = EmptyFieldsDescriptor

  override def updatedFlags(position: Int, flag: Boolean): Flags = {
    new GenericFlags(updatedValue(position, flag))
  }
}

/** A FieldsDescriptor that doesn't provide any field name to field position
 * mappings. */
object EmptyFieldsDescriptor extends FieldsDescriptor {
  val fieldPositions: Map[String, Int] = immutable.Map()
}
