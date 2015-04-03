package nest.sparkle.util

object StringUtil {

  /** return the trailing component in a token separated string */
  def lastPart(path: String, separator: String = "/"): String = {
    val end = path.lastIndexOf(separator)
    if (end >= 0) {
      path.substring(end + 1)
    } else {
      path
    }
  }
  
  /** return an optional suffix to a string: the part after a separator */
  def findSuffix(source:String, separator:String = "."):Option[String] = {
    val end = source.lastIndexOf(separator)
    if (end >= 0) {
      Some(source.substring(end + 1))
    } else {
      None
    }
  }

  /** return the leading component in a token separated string */
  def firstPart(path: String, separator: String = "/"): String = {
    val end = path.lastIndexOf(separator)
    if (end >= 0) {
      path.substring(0, end)
    } else {
      path
    }
  }

}
