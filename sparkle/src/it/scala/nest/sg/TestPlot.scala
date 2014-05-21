package nest.sg

import org.scalatest.FunSuite
import org.scalatest.Matchers

class TestPlot extends FunSuite with Matchers {
  ignore("a simple plot") {
    Plot.plot(List(4,5,6,7,8), "test1")
    Thread.sleep(10000000)
  }
}