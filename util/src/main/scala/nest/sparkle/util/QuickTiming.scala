package nest.sparkle.util

import java.text.DecimalFormat

object QuickTiming {

  /** Run a function, and print how long it takes. */
  def printTime[T](name: String)(fn: => T): T = {
    val start = System.nanoTime()
    val result = fn
    val duration = (System.nanoTime() - start) / 1000000.0
    println(s"$name ${duration}ms")
    result
  }

  /** Run a function, and return how long it takes. */
  def timed(fn: => Unit): Double = {
    val start = System.nanoTime()
    fn
    val duration = (System.nanoTime() - start) / 1000000.0
    duration
  }

  /** Return a human readable string for a millisecond duration */
  def millisString(millis: Double): String = {
    if (millis > 1000) {
      val seconds = numberString(millis / 1000)
      s"$seconds seconds"
    } else {
      s"${numberString(millis)} milliseconds"
    }
  }

  /** Return a human readable string for a number: comma separated 
   *  thousands for big numbers, and not too much resolution for fractions. */
  def numberString(number: Double): String = {
    number match {
      case _ if (number >= 100)  => commas.format(number)
      case _ if (number >= 10)   => oneDecimal.format(number)
      case _ if (number >= .001) => thousandths.format(number)
      case _                     => number.toString
    }
  }

  private val commas = new DecimalFormat("#,###")
  private val oneDecimal = new DecimalFormat("#,###.0")
  private val thousandths = new DecimalFormat("0.000")

}