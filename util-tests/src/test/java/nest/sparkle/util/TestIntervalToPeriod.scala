package nest.sparkle.util

import scala.collection.SortedSet

import org.joda.time.DurationFieldType
import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.measure.Milliseconds
import nest.sparkle.util.IntervalToPeriod._
import scala.concurrent.duration._

class TestIntervalToPeriod extends FunSuite with Matchers {
  test("roundValue on a big number") {
    roundValue(math.pow(10,9).toLong + 11, SortedSet(1,5,10,25,50)) shouldBe math.pow(10,9).toLong
  }

  test("roundValue on a small number") {
    roundValue(1, SortedSet(5,10,50)) shouldBe 5
  }

  test("round 75 milliseconds to period") {
    val millis = Milliseconds(75)
    millisToRoundedPeriod(millis) shouldBe Period(100, DurationFieldType.millis)
  }

  test("round 20 minutes to period") {
    val millis = Milliseconds(20.minutes.toMillis)
    millisToRoundedPeriod(millis) shouldBe Period(30, DurationFieldType.minutes)
  }

  test("round 45 minutes to period") {
    val millis = Milliseconds(45.minutes.toMillis)
    millisToRoundedPeriod(millis) shouldBe Period(60, DurationFieldType.minutes)
  }

  test("round 1 hour to period") {
    val millis = Milliseconds(1.hour.toMillis)
    millisToRoundedPeriod(millis) shouldBe Period(60, DurationFieldType.minutes)
  }

  test("round 12 hours to period") {
    val millis = Milliseconds(12.hours.toMillis)
    millisToRoundedPeriod(millis) shouldBe Period(12, DurationFieldType.hours)
  }

  test("round 2 weeks to period") {
    val millis = Milliseconds(14.days.toMillis)
    millisToRoundedPeriod(millis) shouldBe Period(1, DurationFieldType.weeks)
  }

  test("round 1 years to period") {
    val millis = Milliseconds((365*1).days.toMillis)
    millisToRoundedPeriod(millis) shouldBe Period(1, DurationFieldType.years)
  }

  test("round 3 years to period") {
    val millis = Milliseconds((365*3).days.toMillis)
    millisToRoundedPeriod(millis) shouldBe Period(1, DurationFieldType.years)
  }


}
