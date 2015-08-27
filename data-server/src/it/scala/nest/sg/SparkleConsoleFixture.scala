package nest.sg

import nest.sparkle.test.PortsTestFixture
import nest.sparkle.util.FlexibleConfig

object SparkleConsoleFixture {
}

trait SparkleConsoleFixture extends FlexibleConfig {


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
