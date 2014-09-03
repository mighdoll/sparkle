package nest.sparkle.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import nest.sparkle.util.ConfigUtil
import nest.sparkle.util.LogUtil

trait SparkleTestConfig {
  var loggingInitialized = false

  /** subclasses may override to modify the Config for particular tests */
  def configOverrides: List[(String, Any)] = List()

  /** subclasses may override to add a .conf file */
  def testConfigFile: Option[String] = Some("tests")

  /** return the outermost Config object. Also triggers logging initialization */
  lazy val rootConfig: Config = {
    val root = testConfigFile match {
      case Some(confFile) => ConfigFactory.load(confFile)
      case None           => ConfigFactory.load()
    }

    val withOverrides = ConfigUtil.modifiedConfig(root, configOverrides: _*)

    // uncomment to print out the config
    //    println(withOverrides.root.render())

    initializeLogging(withOverrides)
    withOverrides
  }

  /** setup logging for sparkle. Triggered automatically when the caller accesses
    * rootConfig. Idempotent.
    */
  def initializeLogging(root: Config): Unit = {
    if (!loggingInitialized) {
      LogUtil.configureLogging(root)
    }
  }

}
