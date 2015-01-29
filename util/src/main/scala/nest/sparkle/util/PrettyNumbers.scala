package nest.sparkle.util

import java.text.NumberFormat

/** Adds a .pretty method to Long and Int, for pretty-printing with commas at the thousands mark. */
object PrettyNumbers {
  lazy val integerFormat = NumberFormat.getIntegerInstance

  object implicits {
    implicit class PrettyLong(number:Long) {
      def pretty:String = integerFormat.format(number)
    }

    implicit class PrettyInt(number:Int) {
      def pretty:String = integerFormat.format(number)
    }
  }
}
