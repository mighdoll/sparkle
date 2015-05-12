package nest.sparkle.datastream

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import org.joda.time.DateTimeZone
import org.scalacheck.Test.Passed
import org.scalacheck.{Test, Prop}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}
import spire.math._

import nest.sparkle.datastream.StreamGeneration._
import nest.sparkle.measure.DummySpan
import nest.sparkle.util.StringToMillis._
import nest.sparkle.util.{Period, PeriodWithZone}

/**
 */
class TestDataStreamReduceByPeriod extends FunSuite with Matchers with PropertyChecks {
  val simpleEvents = {
    val simpleEventStrings = Seq(
      // 8 seconds in the first hour
      ("2014-12-01T00:00:00.000", 1),
      ("2014-12-01T00:10:00.000", 3),
      ("2014-12-01T00:40:00.000", 2),
      ("2014-12-01T00:50:00.000", 2),
      // one hour nothing
      // 2 seconds in the third hour
      ("2014-12-01T02:00:00.000", 2)
    )
    simpleEventStrings.map { case (time, value) => (time.toMillis, value) }
  }

  def reduceSum[K: ClassTag: TypeTag: Numeric, V: ClassTag: TypeTag: Numeric] // format: OFF
      ( parts: Seq[Seq[(K, V)]], period:String, range: SoftInterval[K] = SoftInterval.empty[K] )
      : DataArray[K,Option[V]] = { // format: ON
    val stream = createStream(parts)
    val periodWithZone = PeriodWithZone(Period.parse(period).get, DateTimeZone.UTC)
    implicit val span = DummySpan
    val reduced = stream.reduceByPeriod(periodWithZone, range, ReduceSum[V](), true)
    val dataArrays = reduced.reducedStream.data.toBlocking.toList
    val optResult = dataArrays.reduceLeftOption (_ ++ _)
    optResult.getOrElse(DataArray.empty)
  }
  
  def testSumSimpleByHour[K: ClassTag: TypeTag: Numeric, V: ClassTag: TypeTag: Numeric] // format: OFF
      ( parts: Seq[Seq[(K, V)]])
      : Boolean =  { // format: ON

    val results = reduceSum(parts, "1 hour")
    validateSimpleByHour(results)
  }
  
  def validateSimpleByHour[K, V](data: DataArray[K, Option[V]]): Boolean = {
    data.length shouldBe 3
    data(0) shouldBe ("2014-12-01T00:00:00.000".toMillis -> Some(8))
    data(1) shouldBe ("2014-12-01T01:00:00.000".toMillis -> None)
    data(2) shouldBe ("2014-12-01T02:00:00.000".toMillis -> Some(2))
    true // throws if it fails
  }

  test("reduceByPeriod: sum a stream with a fixed partitioning") {
    val parts = Seq(simpleEvents)
    testSumSimpleByHour(parts)
  }
  
  test("reduceByPeriod: sum a simple stream with various partitionings") {
    val prop = Prop.forAllNoShrink(threeParts(simpleEvents)) { parts =>
      testSumSimpleByHour(parts)
    }
    val result = Test.check(prop)(_.withMinSuccessfulTests(5))
    result.status shouldBe Passed
  }

  test("reduceByPeriod: sum with 30 minute period, many partitions, end < data") {
    val parts = simpleEvents.map { element => Seq(element)}
    val range = SoftInterval(None, Some("2014-12-01T01:00:00.000".toMillis))
    val result = reduceSum(parts, "30 minute", range)

    result.keys shouldBe Seq(
      "2014-12-01T00:00:00.000".toMillis,
      "2014-12-01T00:30:00.000".toMillis
    )
    result.values shouldBe Seq(
      Some(4),
      Some(4)
    )
  }

  test("reduceByPeriod: sum with 30 minute period, many partitions, end > data") {
    val parts = simpleEvents.map { element => Seq(element)}
    val range = SoftInterval(None, Some("2014-12-01T03:00:00.000".toMillis))
    val result = reduceSum(parts, "30 minute", range)

    result.keys shouldBe Seq(
      "2014-12-01T00:00:00.000".toMillis,
      "2014-12-01T00:30:00.000".toMillis,
      "2014-12-01T01:00:00.000".toMillis,
      "2014-12-01T01:30:00.000".toMillis,
      "2014-12-01T02:00:00.000".toMillis,
      "2014-12-01T02:30:00.000".toMillis
    )
    result.values shouldBe Seq(
      Some(4),
      Some(4),
      None,
      None,
      Some(2),
      None
    )
  }

  test("reduceByPeriod: no data") {
    val results = reduceSum[Long,Long](Seq(Seq()), "1 hour")
    println(results)
  }


}