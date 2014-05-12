package nest.sg

import org.scalatest.FunSuite
import org.scalatest.Matchers

class TestPlot extends FunSuite with Matchers {
  ignore("a simple plot") {
    Plot.store(List(1,2,3), "test1")
    Thread.sleep(1000000)
  }
}