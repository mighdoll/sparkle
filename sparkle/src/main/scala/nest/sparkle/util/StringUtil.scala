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

}