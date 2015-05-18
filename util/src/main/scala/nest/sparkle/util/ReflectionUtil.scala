package nest.sparkle.util

import java.lang.reflect.Field
import scala.reflect.runtime.universe._
import scala.reflect.ClassTag
import scala.collection.mutable

/** a few modest utilities for using scala reflection */
object ReflectionUtil {
  val classTagCache = mutable.Map[TypeTag[_], ClassTag[_]]()

  /** return a class tag from type tag */
  def classTag[A:TypeTag]:ClassTag[A] = {
    val theTag = typeTag[A]

    val classTag = classTagCache.getOrElseUpdate(theTag,
      ClassTag(theTag.mirror.runtimeClass(theTag.tpe)))
    classTag.asInstanceOf[ClassTag[A]]
  }

  /** given a case class type tag, return a list of the case class field names */
  def caseClassFields[T:TypeTag]:Seq[String] = {
    typeOf[T].members.collect {
      case m:MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString
    }.toVector
  }

}