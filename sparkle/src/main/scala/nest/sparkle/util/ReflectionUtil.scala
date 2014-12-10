package nest.sparkle.util
import scala.reflect.runtime.universe._
import scala.reflect.ClassTag

object ReflectionUtil {
  def classTag[A](typeTag:TypeTag[A]):ClassTag[A] = {
    ClassTag(typeTag.mirror.runtimeClass(typeTag.tpe))
  }
    
}