package nest.sparkle.util

object QuickTiming {

  /** run a function, and print how long it takes */
  def printTime[T](name: String)(fn: => T): T = {
    val start = System.nanoTime()
    val result = fn
    val duration = (System.nanoTime() - start) / 1000000.0
    println(s"$name ${duration}ms")
    result
  }

}