package nest.sparkle.datastream

import scala.concurrent.duration._

import nest.sparkle.datastream.LargeReduction._
import nest.sparkle.measure.{Measurements, DummySpan, Span}
import nest.sparkle.store.cassandra.{ActorSystemFixture, CassandraStoreFixture}
import nest.sparkle.time.protocol.{TestDataService, TestServiceWithCassandra}
import nest.sparkle.util.{ConfigUtil, SparkleApp}
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.FutureAwait.Implicits._

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

  val jig = new TestJig("reductionTest", warmups = 0, runs = 1, pause = false)

  runProtocolTest()

  shutdown()

  println("all done")

  def runProtocolTest(): Unit = {
    val sparkleConfig = ConfigUtil.configForSparkle(rootConfig)
    val testColumnPath = "reduce/test"
    CassandraStoreFixture.withTestDb(sparkleConfig, "reduction_main") { testDb =>
      ActorSystemFixture.withTestActors("reduction-main") { actorSystem =>
        TestDataService.withTestService(testDb, actorSystem) {testService =>
          preloadStore(1.hour, testColumnPath, testService)(jig.span, testService.executionContext)

          jig.run {span =>
            byPeriodLocalProtocol("1 day", testColumnPath, testService)(span).await(1.minute)
          }
        }
      }
    }
  }

  def runStreamOnlyTest(): Unit = {
    jig.run{ implicit span =>
//    toOnePart(30.seconds)
      byPeriod(30.seconds, "1 day")
    }
  }
}

class TestJig(name: String, warmups:Int = 2, runs:Int = 1, pause:Boolean = false)
             ( implicit measurements: Measurements) {

  //    Thread.sleep(pause.toMillis) // so that the start time will be clear in the profiler
  implicit val span = Span.prepareRoot(name)

  def run[T]
      ( fn: Span => T )
      : Seq[T] = {

    (0 until warmups).foreach {_ =>
      fn(DummySpan)
    }

    if (pause) {
      println("press return to continue")
      Console.in.readLine()
      println("continuing")
    }

    (0 until runs).map {_ =>
      Span("total").time {
        fn(span)
      }
    }.toVector

  }
}
