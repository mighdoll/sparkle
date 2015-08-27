package nest.sparkle.util

import com.typesafe.config.{ConfigFactory, Config}

trait FlexibleConfig {

  /** subclasses may override to modify the Config for particular tests */
  def configOverrides: Seq[(String, Any)] = List()

  /** subclasses may override to add a .conf file */
  def testConfigFile: Option[String] = Some("tests")

  /** return the outermost Config object. Also triggers logging initialization */
  lazy val rootConfig: Config = {
    val baseConfig = ConfigFactory.load()
    val root = testConfigFile match {
      case Some(confFile) =>
        val config = ConfigFactory.parseResources(confFile+".conf").resolve()
        val rendered = config.root.render
        config.withFallback(baseConfig)
      case None           =>
        baseConfig
    }

    val withOverrides = ConfigUtil.modifiedConfig(root, configOverrides: _*)

    ConfigUtil.dumpConfigToFile(withOverrides)
    initLogging(withOverrides)
    withOverrides
  }

  /** setup logging for sparkle. Triggered automatically when the caller accesses
    * rootConfig. Idempotent.
    */
  protected def initLogging(config: Config) {
    LogUtil.configureLogging(config)
  }
}
