package nest.sparkle.datastream

import scala.concurrent.duration._

import nest.sparkle.datastream.LargeReduction.{byPeriod, toOnePart}
import nest.sparkle.measure.{Measurements, DummySpan, Span}
import nest.sparkle.util.{ConfigUtil, SparkleApp}
import nest.sparkle.util.ConfigUtil.sparkleConfigName

/** a test driver for reduction tests
 */
object ReductionMain extends SparkleApp {
  override def appName = "LargeReduction"
  override def appVersion = "0.1"
  override val overrides = Seq(
    s"$sparkleConfigName.measure.metrics-gateway.enable" -> false,
    s"$sparkleConfigName.measure.tsv-gateway.enable" -> true
  )

  initialize()

  val testByPeriod = {span:Span => byPeriod(30.seconds, "1 day")(span) }
  val testToOnePart = {span:Span => toOnePart(30.seconds)(span) }

  TestJig.run("reductionTest", warmups = 0, runs = 30)(testByPeriod)
  shutdown()
}

object TestJig {
  def run[T]
      ( name:String, warmups:Int = 2, runs:Int = 1, pause:Duration = 5.seconds)
      ( fn: Span => T )
      ( implicit measurements: Measurements)
      : Seq[T] = {

    (0 until warmups).foreach {_ =>
      fn(DummySpan)
    }
    println("press return to continue")
    Console.in.readLine()
    println("continuing")

//    Thread.sleep(pause.toMillis) // so that the start time will be clear in the profiler

    (0 until runs).map {_ =>
      implicit val span = Span.prepareRoot(name)
      Span("total").time {
        fn(span)
      }
    }.toVector

  }
}
