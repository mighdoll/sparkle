package nest.sparkle.util

import org.scalatest.{FunSuite, Matchers}

import org.joda.time.{DateTimeZone, DateTime, DurationFieldType}
import nest.sparkle.util.StringToMillis._

class TestPeriod extends FunSuite with Matchers {

  DateTimeZone.setDefault(DateTimeZone.UTC)

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

  test("round date down by millis") {
    val date = new DateTime("2014-07-06T04:33:01.326".toMillis)
    val period = Period.parse("25 milliseconds").get
    val rounded = period.roundDate(date)
    rounded shouldBe new DateTime("2014-07-06T04:33:01.325".toMillis)
  }

  test("round date down by seconds (date seconds on period boundary)") {
    val date = new DateTime("2014-07-06T04:01:10.000".toMillis)
    val period = Period.parse("10 seconds").get
    val rounded = period.roundDate(date)
    rounded shouldBe new DateTime("2014-07-06T04:01:10.000".toMillis)
  }

  test("round date down by seconds (date seconds > period boundary)") {
    val date = new DateTime("2014-07-06T04:01:42.000".toMillis)
    val period = Period.parse("10 seconds").get
    val rounded = period.roundDate(date)
    rounded shouldBe new DateTime("2014-07-06T04:01:40.000".toMillis)
  }

  test("round date down by seconds (date seconds < period boundary)") {
    val date = new DateTime("2014-07-06T04:01:09.000".toMillis)
    val period = Period.parse("10 seconds").get
    val rounded = period.roundDate(date)
    rounded shouldBe new DateTime("2014-07-06T04:01:00.000".toMillis)
  }

  test("round date down by minutes") {
    val date = new DateTime("2014-07-06T04:33:01.000".toMillis)
    val period = Period.parse("15 minutes").get
    val rounded = period.roundDate(date)
    rounded shouldBe new DateTime("2014-07-06T04:30:00.000".toMillis)
  }

  test("round date down by hours") {
    val date = new DateTime("2014-07-06T05:01:00.000".toMillis)
    val period = Period.parse("2 hours").get
    val rounded = period.roundDate(date)
    rounded shouldBe new DateTime("2014-07-06T04:00:00.000".toMillis)
  }

  test("round date down by months") {
    val date = new DateTime("2014-07-06T05:01:00.000".toMillis)
    val period = Period.parse("3 months").get
    val rounded = period.roundDate(date)
    rounded shouldBe new DateTime("2014-06-01T00:00:00.000".toMillis)
  }

}
