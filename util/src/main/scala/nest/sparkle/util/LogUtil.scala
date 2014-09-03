package nest.sparkle.util

import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.config.Config

/** Utilities for logging */
object LogUtil {
  
  private val configured: AtomicBoolean = new AtomicBoolean(false)
  
  /** setup java logging based on .conf settings for levels, file sizes, etc. */
  def configureLogging(rootConfig: Config): Unit = {
    if (configured.compareAndSet(false,true)) {
      val sparkleConfig = ConfigUtil.configForSparkle(rootConfig)
      val providerName = sparkleConfig.getString("logging.provider")
      val providerClassName = providerName match {
        case s if s.equalsIgnoreCase("log4j")   => Some("nest.sparkle.util.ConfigureLog4j")
        case s if s.equalsIgnoreCase("logback") => Some("nest.sparkle.util.ConfigureLogback")
        case s if s.equalsIgnoreCase("none")    => None
        case _                                  => {
          println(s"invalid logger provider $providerName specified. No logger configuration performed")
          None
        }
      }

      providerClassName foreach { className =>
        val provider = Instance.objectByClassName[ConfigureLog](className)
        provider.configureLogging(sparkleConfig)
      }
    }
  }
  
  
  
  /** (for debug logging) create a key=value string from an optional value */
  def optionLog[T](name: String, option: Opt[T]): Option[String] = {
    option.map { value => s"$name=${value.toString}" }
  }
}

/** interface for log4j or logback configuration */
trait ConfigureLog {
  def configureLogging(sparkleConfig: Config): Unit
}