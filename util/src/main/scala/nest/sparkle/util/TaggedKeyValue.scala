package nest.sparkle.util

import scala.reflect.runtime.universe._

/** a class containing a key and value typeTags */
trait TaggedKeyValue {
  def keyType: TypeTag[_]
  def valueType: TypeTag[_]
}

/** support for writing an Extractor on a TaggedKeyValue. subclasses
  * implement `cast` to return the appropriate typed object.
  *
  * The use here is to:
  * a) recover the dynamic types that we've e.g. recorded in the column catalog
  *   (but aren't known statically until we fetch the catalog entry)
  * b) use some nice clear case statements when we do the recovery: see e.g. SparseColumnReader's
  *   matching on the catalogInfo.
  */
abstract class TaggedKeyValueExtractor[T: TypeTag, U: TypeTag, V] {
  def cast: V
  def unapply(taggedEvent: TaggedKeyValue): Option[V] = {
    val expectA = typeTag[T].tpe
    val expectB = typeTag[U].tpe

    if (taggedEvent.keyType.tpe <:< expectA && taggedEvent.valueType.tpe <:< expectB) {
      Some(cast)
    } else {
      None
    }
  }
}


