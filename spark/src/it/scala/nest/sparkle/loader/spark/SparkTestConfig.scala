package nest.sparkle.loader.spark

import nest.sparkle.test.SparkleTestConfig

trait SparkTestConfig extends SparkleTestConfig {
  override def testConfigFile = Some("spark-tests")
}
