package nest.sparkle.time.transform
import nest.sparkle.util.BooleanOption._


/** Support trait for matching a transform by name */
trait TransformMatcher {
  type TransformType // e.g. ColumnTransform 
  def prefix:String  // e.g. "summarize"
  def suffixMatch:PartialFunction[String,TransformType] // match e.g. "max", "min", etc.

  /** match a Transform string selector, and return a ColumnTransform to be applied to each source column */
  def unapply(transform:String):Option[TransformType] = {
    val lowerCase = transform.toLowerCase
    for {
      _ <- lowerCase.startsWith(prefix).toOption 
      suffix = lowerCase.stripPrefix(prefix) 
      transform <- suffixMatch.lift(suffix)
    } yield {
      transform
    }
  }

}