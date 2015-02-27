package nest.sg

import scala.concurrent.duration._
import org.scalatest.FunSuite
import org.scalatest.Matchers
import rx.lang.scala.Observable

import nest.sparkle.util.Resources

/** These are currently development jigs, manually uncommented and run one a time
  * rather than as part of a test infrastructure.
  *
  * TODO make these into integration tests.
  */
class TestPlot extends FunSuite with Matchers with SparkleConsoleFixture {
  val measuresDirectory = Resources.filePathString("sample-measures")

  ignore("plot an observable stream of sine") {
    withSparkleConsole { console =>
      val items = Observable.interval(300.milliseconds).map{ x =>
        math.sin((math.Pi * x) / 20)
      }
      console.plotStream(items)
      Thread.sleep(1000*60*60)
    }
  }

  ignore("plot a list of items") {
    withSparkleConsole { console =>
      console.plotCollection[Int](List(10,11,12))
      Thread.sleep(1000*60*60)
    }
  }

  ignore("load some data and plot, with time and reduceMax") {
    withSparkleConsole { console =>
      console.loadFiles(measuresDirectory)
      val plot = PlotParameters("spans/duration")
        .withZoomTransform("reduceMax")
        .withXAxis(true)
        .withTime(true)
      console.plotColumn(plot)
      Thread.sleep(1000*60*60)
    }
  }

  ignore("load some data and plot, simple") {
    withSparkleConsole { console =>
      console.loadFiles(measuresDirectory)
      console.plotColumn("spans/duration")
      Thread.sleep(1000*60*60)
    }
  }

  ignore("start console and do nothing") {
    withSparkleConsole { console =>
      console.loadFiles(measuresDirectory)
      Thread.sleep(1000*60*60)
    }
  }

}