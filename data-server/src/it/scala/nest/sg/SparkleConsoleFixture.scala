package nest.sg

import nest.sparkle.test.PortsTestFixture

object SparkleConsoleFixture {
}

trait SparkleConsoleFixture {
  def withSparkleConsole[T](fn: SparkleConsole=>T): T = {
    val console = new SparkleConsole {
      override def configOverrides:Seq[(String,Any)] = {
        super.configOverrides ++ PortsTestFixture.sparklePortsConfig()
      }
    }

    try {
      fn(console)
    } finally {
      console.close()
    }

  }
}
