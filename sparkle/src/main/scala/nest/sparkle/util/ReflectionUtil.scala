package nest.sparkle.util
import scala.reflect.runtime.universe._
import scala.reflect.ClassTag

object ReflectionUtil {
  def classTag[A:TypeTag]:ClassTag[A] = {
    val theTag = typeTag[A]
    ClassTag(theTag.mirror.runtimeClass(theTag.tpe))
  }
    
}