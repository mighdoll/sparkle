package nest.sparkle.util

/** add .toOption method to Boolean */
object BooleanOption {
  /** add .toOption method to Boolean */
  implicit class BooleanToOption(val value: Boolean) extends AnyVal {
    final def toOption: Option[Unit] =
      if (value) {
        Some(())
      } else {
        None
      }
  }
}
