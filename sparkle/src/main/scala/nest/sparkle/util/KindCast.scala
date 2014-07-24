package nest.sparkle.util
import scala.language.higherKinds

object KindCast {

  /** Do a constrained asInstanceOf cast that's safer than a raw asInstanceOf cast because 
   *  calls to castKind will only compile on certain combinations of types. 
   *  
   *  The input and output types of castKind must be producable by
   *  the same type constructor.
   *  
    * castKind will cast a List[Any] to a List[Double], but attempting to
    * castKind a List[Any] to a String will fail at compile time.
    */
  def castKind[T[_], U](value: T[_]): T[U] = {
    value.asInstanceOf[T[U]]
  }

}