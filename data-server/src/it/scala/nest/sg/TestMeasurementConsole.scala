package nest.sg

import org.scalatest.{Matchers, FunSuite}
import nest.sparkle.util.PrettyNumbers.implicits._

object ConsoleSumReductionMain extends App with SparkleConsoleFixture {
  withSparkleConsole { console =>
    import console._

    use("console_sum_reduction")
    store.format()

    loadFiles("/tmp/sparkle-measurement")

    writeStore
    println("Last ReduceBlock Time:")
    val reduceBlock = lastMeasurement("Sum.reduceBlock")
    reduceBlock.printAll()

    println("Last FetchBlock Time:")
    val fetchBlock = lastMeasurement("Sum.readEventRowsA.fetchBlock")
    fetchBlock.printAll()

    println("Last Total Time:")
    val total = lastMeasurement("reductionTest.requestResponse")
    total.printAll()

    println("Last Generate Time:")
    val generates = lastMeasurement("preload.generateTestData")
    generates.printAll()

    println("Trend ReduceBlock Times")
    val allReduceBlocks = measurementsData("Sum.reduceBlock")
    allReduceBlocks.foreach { case (traceId, intervals) =>
      println(s"  $traceId: ${intervals.totalDuration.pretty} microseconds")
    }

    println("Trend FetchBlock Times")
    val allFetchBlocks = measurementsData("Sum.readEventRowsA.fetchBlock")
    allFetchBlocks.foreach { case (traceId, intervals) =>
      println(s"  $traceId: ${intervals.totalDuration.pretty} microseconds")
    }
  }
}
