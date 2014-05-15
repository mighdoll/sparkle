package nest.sg

import org.scalatest.FunSuite
import org.scalatest.Matchers

class TestPlot extends FunSuite with Matchers {
  ignore("a simple plot") {
    Plot.plot(List(1,2,3), "test1")
    Thread.sleep(10000000)
  }
}