package nest.sg

import scala.concurrent.duration._
import org.scalatest.FunSuite
import org.scalatest.Matchers
import rx.lang.scala.Observable

class TestPlot extends FunSuite with Matchers with SparkleConsoleFixture {
//  ignore("a stream plot") {
//    val items = Observable.interval(300.milliseconds).map{ x =>
//      math.sin((math.Pi * x) / 20)
//    }
//    Plot.plotStream(items)
//    Thread.sleep(10000000)
//  }

  ignore("a simple plot") {
    withSparkleConsole { console =>
      console.plot(List(1,2,3))
      Thread.sleep(1000*60*60)
    }
  }
  

 }