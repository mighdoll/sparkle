package nest.sg

import nest.sparkle.util.ConfigUtil.sparkleConfigName
import SparkleConsoleFixture.takePort

object SparkleConsoleFixture {
  // we give each test its own set of ports, so that multiple tests can run in parallel
  private var nextPort = 22222
  def takePort():Int = {
    val port = nextPort
    nextPort += 1
    port
  }
}

trait SparkleConsoleFixture {
  def withSparkleConsole[T](fn: SparkleConsole=>T): T = {
    val console = new SparkleConsole {
      override def configOverrides:Seq[(String,Any)] = {
        super.configOverrides ++ Seq(
          s"$sparkleConfigName.port" -> takePort(),
          s"$sparkleConfigName.admin.port" -> takePort(),
          s"$sparkleConfigName.web-socket.port" -> takePort()
        )
      }
    }

    try {
      fn(console)
    } finally {
      console.close()
    }

  }
}
