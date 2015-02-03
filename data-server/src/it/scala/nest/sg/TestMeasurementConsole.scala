package nest.sg

import scala.util.matching.Regex

import org.scalatest.{Matchers, FunSuite}
import nest.sparkle.util.PrettyNumbers.implicits._

object ConsoleSumReductionMain extends App with SparkleConsoleFixture {
  withSparkleConsole { console =>
    import console._

    use("console_sum_reduction")
    store.format()
    loadFiles("/tmp/sparkle-measurement")
    val all = allIntervals()

    all.foreach { case NamedTraceIntervals(name, traceIntervals) =>
      println(s"Last $name time:")
      val lastTraceInterval = traceIntervals.last
      lastTraceInterval.intervals.printAll()
    }

    printTrends(all, """.*\.reduceBlock""", """.*\.fetchBlock""")
  }

  def printTrends(allNamedTraceIntervals:Seq[NamedTraceIntervals], names:String*): Unit = {
    val matchers = names.map (_.r)
    def matchesName(name:String):Boolean = {
      val foundMatch = matchers.find{regex => regex.findFirstIn(name).isDefined }
      foundMatch.isDefined
    }

    for {
      namedTraceIntervals <- allNamedTraceIntervals
      NamedTraceIntervals(name, traceIntervals) = namedTraceIntervals
      if matchesName(name)
    } {
      printTrend(namedTraceIntervals)
    }
  }

  def printTrend(namedTraceIntervals:NamedTraceIntervals): Unit = {
    val NamedTraceIntervals(name, traceIntervals) = namedTraceIntervals
    println(s"Trend $name")
    traceIntervals.foreach { case TraceIntervals(traceId, intervals) =>
      println(s"  $traceId: ${intervals.totalDuration.pretty} microseconds")
    }
  }
}
