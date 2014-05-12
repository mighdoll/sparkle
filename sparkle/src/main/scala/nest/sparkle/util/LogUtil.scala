package nest.sparkle.util

/** Utilities for debug logging */
object LogUtil {
  /** (for debug logging) create a key=value string from an optional value */
  def optionLog[T](name: String, option: Opt[T]): Option[String] = {
    option.map { value => s"$name=${value.toString}" }
  }
}