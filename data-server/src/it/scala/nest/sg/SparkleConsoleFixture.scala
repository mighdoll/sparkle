package nest.sg

trait SparkleConsoleFixture {
  def withSparkleConsole[T](fn: SparkleConsole=>T): T = {
    val console = new SparkleConsole {}

    try {
      fn(console)
    } finally {
      console.close()
    }

  }
}
