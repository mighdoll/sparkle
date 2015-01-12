package nest.sparkle.measure

import org.scalatest.FunSuite
import org.scalatest.Matchers
import scala.concurrent.duration._

class TestCalibratedNanos extends FunSuite with Matchers {
  test("calibrated Nanos should be close to current time") {
    val convertedFromNanos = CalibratedNanos.toEpochMillis(NanoSeconds.current())
    val delta = convertedFromNanos.value - System.currentTimeMillis()
    Math.abs(delta) should be < 2.seconds.toMillis 
  }
}