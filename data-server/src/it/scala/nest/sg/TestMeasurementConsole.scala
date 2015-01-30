package nest.sg

import org.scalatest.{Matchers, FunSuite}
import nest.sparkle.util.PrettyNumbers.implicits._

class TestMeasurementConsole extends FunSuite with Matchers with SparkleConsoleFixture {
  ignore("measurements for reduceBlock") { // TODO convert to proper test
    withSparkleConsole { console =>
      import console._

      loadFiles("/tmp/sparkle-measurement")

      println("Last ReduceBlock Time:")
      val reduceBlock = lastMeasurement("reductionTest.reduceBlock")
      reduceBlock.printAll()

      println("Last Total Time:")
      val total = lastMeasurement("reductionTest.total")
      total.printAll()

      println("Last Generate Time:")
      val generates = lastMeasurement("reductionTest.generateTestData")
      generates.printAll()

      println("Trend ReduceBlock Times")
      val allReduceBlocks = measurementsData("reductionTest.reduceBlock")
      allReduceBlocks.foreach { case (traceId, intervals) =>
        println(s"  $traceId: ${intervals.totalDuration.pretty} microseconds")
      }

    }
  }
}
