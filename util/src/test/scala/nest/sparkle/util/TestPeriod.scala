package nest.sparkle.util

import org.scalatest.{FunSuite, Matchers}

import org.joda.time.{DateTime, DurationFieldType}
import nest.sparkle.util.StringToMillis._

class TestPeriod extends FunSuite with Matchers {

  test("10 days") {
    Period.parse("10 days") shouldBe Some(Period(10, DurationFieldType.days))
  }
  
  test("3 years") {
    Period.parse("3 years") shouldBe Some(Period(3, DurationFieldType.years))
  }
  
   test("1500 microseconds") {
    Period.parse("1500 microseconds") shouldBe Some(Period(1, DurationFieldType.millis))
  }

  test("zero length periods should be ok") {
    Period.parse("0 weeks") shouldBe Some(Period(0, DurationFieldType.weeks))
  }
  
  test("negative length periods should be illegal") {
    Period.parse("-1 days") shouldBe None
  }
  
  test("round date down") {
    val date = new DateTime("2014-07-06T04:01:00.000".toMillis)
    val period = Period.parse("1 hour").get
    val rounded = period.roundDate(date)
    rounded shouldBe new DateTime("2014-07-06T04:00:00.000".toMillis)
  }

}