package nest.sparkle.datastream

import scala.concurrent.duration._

import nest.sparkle.datastream.LargeReduction.{byPeriod, toOnePart}
import nest.sparkle.measure.{DummySpan, Span}
import nest.sparkle.util.{ConfigUtil, SparkleApp}
import nest.sparkle.util.ConfigUtil.sparkleConfigName


/** a test driver for reduction test
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

  implicit val span = Span.prepareRoot("reductionTest")
  TestJig.run()(testToOnePart)

}

object TestJig {
  def run[T](warmups:Int = 2)(fn: Span=>T)(implicit parentSpan:Span):T = {

    (0 until warmups).foreach {_ =>
      fn(DummySpan)
    }
    Span("total").time {
      fn(parentSpan)
    }

  }
}
