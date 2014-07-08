package nest.sparkle.util

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.joda.time.DurationFieldType

class TestPeriod extends FunSuite with Matchers {

  test("10 days") {
    Period.parse("10 days") shouldBe Some(Period(10, DurationFieldType.days))
  }
  
  test("3.5 years") {
    Period.parse("3.5 years") shouldBe Some(Period(3.5, DurationFieldType.years))
  }
  
   test("500 microseconds") {
    Period.parse("500 microseconds") shouldBe Some(Period(.5, DurationFieldType.millis))
  }

  test("zero length periods should be ok") {
    Period.parse("0 weeks") shouldBe Some(Period(0, DurationFieldType.weeks))
  }
  
  test("negative length periods should be illegal") {
    Period.parse("-1 days") shouldBe None
  }

}