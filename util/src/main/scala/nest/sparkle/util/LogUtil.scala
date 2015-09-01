package nest.sparkle.util

import java.util.concurrent.atomic.AtomicBoolean
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

/** Utilities for logging */
object LogUtil {
  
  private var loggingProvider:Option[ConfigureLog] = None

  /** setup java logging based on .conf settings for levels, file sizes, etc. */
  def configureLogging(rootConfig: Config): Unit = synchronized {
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
      loggingProvider = Some(provider)
    }

    LoggerFactory.getLogger("nest.sparkle.util.LogUtil").trace("logging system configured.")
  }

  /** Temporarily set the log level for a single logger during a test.
    * Note that log levels are global to the jvm. Simultaneously running tests may conflict
    * on the desired log level. */
  def withLogLevel[T](loggerName:String, level:String)(fn: =>T):T = synchronized {
    loggingProvider.map {provider =>
      provider.withLogLevel(loggerName, level)(fn)
    }.getOrElse(fn)
  }

  /** Temporarily set the log level for a single logger during a test.
    * Note that log levels are global to the jvm. Simultaneously running tests may conflict
    * on the desired log level. */
  def withLogLevel[T](clazz:Class[_], level:String)(fn: =>T):T = synchronized {
    loggingProvider.map {provider =>
      provider.withLogLevel(clazz.getName, level)(fn)
    }.getOrElse(fn)
  }

  /** (for debug logging) create a key=value string from an optional value */
  def optionLog[T](name: String, option: Opt[T]): Option[String] = {
    option.map { value => s"$name=${value.toString}" }
  }
}

/** interface for log4j or logback configuration */
trait ConfigureLog {
  def configureLogging(sparkleConfig: Config): Unit
  def withLogLevel[T](loggerName:String, level:String)(fn: =>T):T
}