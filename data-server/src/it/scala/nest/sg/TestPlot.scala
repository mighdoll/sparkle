package nest.sg

import scala.concurrent.duration._
import org.scalatest.FunSuite
import org.scalatest.Matchers
import rx.lang.scala.Observable

import nest.sparkle.util.Resources

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
      console.plot(List(1,2,3))
      Thread.sleep(1000*60*60)
    }
  }

  ignore("load some data and plot") {
    withSparkleConsole { console =>
      console.loadFiles(measuresDirectory)
      val plot = ("spans/duration":PlotParameters).copy(
          zoomTransform = "reduceMax", showXAxis=true, timeSeries = true
        )
      console.plotColumn(plot)
      Thread.sleep(1000*60*60)
    }
  }

}