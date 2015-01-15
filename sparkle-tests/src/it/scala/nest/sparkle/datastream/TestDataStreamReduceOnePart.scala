package nest.sparkle.datastream

import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks
import spire.math._
import nest.sparkle.datastream.StreamGeneration._
import org.scalacheck.Prop


class TestDataStreamReduceOnePart extends FunSuite with Matchers with PropertyChecks {
  val table = Table(
    ("keyValues"),
    (Seq(1 -> 10, 2 -> 11, 3 -> 15)),
    (Seq(1 -> 10, 2 -> 11, 3 -> 15, 4 -> 40, 5 -> 50))
  )
  
  
  
  def testSum(parts:Seq[Seq[(Int,Int)]]):Boolean = {
    val keyValues = parts.reduce(_ ++ _)
    val expectedSum = sumValues(keyValues)      
    val stream = createStream(parts)
    val reduced = stream.reduceToOnePart(ReduceSum[Int]())
    val result = reduced.data.toBlocking.toList
    result.length shouldBe 1 
    result.head.values.head shouldBe expectedSum
    true // will throw if tests above fail
  }
  
  test("reduceToOnePart: sum of a stream with values split into varying size arrays") {
    forAll(table) { keyValues =>
      val prop = Prop.forAllNoShrink(threeParts(keyValues)) { parts =>
        testSum(parts)
      }
      prop.check
    }
  }
  
  // (this test doesn't cover new ground, it's useful for debugging individual failures)
  test("reduceToOnePart: sum a stream with a fixed partitioning") { 
    val parts = Seq(
      table(0), Seq(), Seq()
    )
    testSum(parts)
  }
  
}