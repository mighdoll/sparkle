/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.util

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.JavaConverters._

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.{ ILoggingEvent, LoggingEvent }
import ch.qos.logback.classic.{ Level, Logger, LoggerContext }
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.rolling._
import ch.qos.logback.core.{FileAppender, Appender, ConsoleAppender}

import com.typesafe.config.Config

import org.slf4j

/** configure a logback logger based on the config file */
object ConfigureLogback extends ConfigureLog with Log {

  /** configure logging based on the .conf file */
  def configureLogging(sparkleConfig: Config): Unit = {
    slf4j.LoggerFactory.getLogger(slf4j.Logger.ROOT_LOGGER_NAME) match {
      case rootLogger: Logger => configureLogBack(sparkleConfig, rootLogger)
      case x                  => log.warn(s"unsupported logger, can't configure logging: ${x.getClass}")
    }
  }

  /** temporarily set the log level for a logger, e.g. for a noisy unit test*/
  def withLogLevel[T](loggerName:String, level:String)(fn: =>T):T = {
    val logger = slf4j.LoggerFactory.getLogger(loggerName).asInstanceOf[Logger]
    val origLevel = logger.getLevel()
    try {
      val newLevel = Level.toLevel(level)
      logger.setLevel(newLevel)
      fn
    } finally {
      logger.setLevel(origLevel)
    }
  }

  /** configure file based logger for logback, based on settings in the .conf file */
  private def configureLogBack(config: Config, rootLogger: Logger): Unit = {
    val logConfig = config.getConfig("logging")
    val loggerContext = slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    loggerContext.reset() // Remove any configuration from other libraries

    // configure logging levels
    val levels = logConfig.getConfig("levels")
    levels.entrySet().asScala.foreach { entry =>
      val key = entry.getKey.stripPrefix("\"").stripSuffix("\"")
      val logger = if (key == "root") {
        rootLogger
      } else {
        slf4j.LoggerFactory.getLogger(key).asInstanceOf[Logger]
      }
      val level = entry.getValue.unwrapped.toString
      println(s"logback configuring: $key -> $level")
      logger.setLevel(Level.toLevel(level))
    }

    // attach new file appender
    if (logConfig.getBoolean("file.enable")) {
      val append = logConfig.getBoolean("file.append")
      val file = logConfig.getString("file.path")
      val fileAppender =
        if (append) {
          val rollingAppender = new RollingFileAppender[LoggingEvent]
          configureFileLogging(rollingAppender, file, append, loggerContext, logConfig)
          configureRolling(rollingAppender, file, loggerContext, logConfig)
          rollingAppender
        } else {
          val fileAppender = new FileAppender[LoggingEvent]
          configureFileLogging(fileAppender, file, false, loggerContext, logConfig)
          fileAppender
        }

      fileAppender.start()
      rootLogger.addAppender(fileAppender.asInstanceOf[Appender[ILoggingEvent]])
    }

    // Attach console appender if enabled.
    if (logConfig.getBoolean("console.enable")) {
      val consoleAppend = consoleAppender(loggerContext, logConfig)
      rootLogger.addAppender(consoleAppend)
    }

    slf4j.LoggerFactory.getLogger(getClass).info("started logback logging")
  }

  /** return an appender for logging to the console */
  def consoleAppender(loggerContext:LoggerContext, logConfig:Config): Appender[ILoggingEvent] = {
    val consoleAppender = new ConsoleAppender[LoggingEvent]
    consoleAppender.setName("Console")
    consoleAppender.setContext(loggerContext)
    consoleAppender.setTarget("System.out")

    val encoder = new PatternLayoutEncoder
    encoder.setContext(loggerContext)
    val pattern = logConfig.getString("console.pattern")
    encoder.setPattern(pattern)
    encoder.start()
    consoleAppender.setEncoder(encoder.asInstanceOf[Encoder[LoggingEvent]])

    val filter = new ThresholdFilter
    val level = logConfig.getString("console.level")
    filter.setLevel(level)
    filter.start()
    consoleAppender.addFilter(filter.asInstanceOf[Filter[LoggingEvent]])

    consoleAppender.start()
    consoleAppender.asInstanceOf[Appender[ILoggingEvent]]
  }

  /** configure the a file logging appender (logging thresholds, log line pattern, etc.) */
  def configureFileLogging
      ( fileAppender: FileAppender[LoggingEvent],
        file: String,
        append: Boolean,
        loggerContext: LoggerContext,
        logConfig: Config )
      : Unit = {
    fileAppender.setName("File")
    fileAppender.setContext(loggerContext)
    fileAppender.setFile(file)
    fileAppender.setAppend(append)

    val encoder = new PatternLayoutEncoder
    encoder.setContext(loggerContext)
    val pattern = logConfig.getString("file.pattern")
    encoder.setPattern(pattern)
    encoder.start()
    fileAppender.setEncoder(encoder.asInstanceOf[Encoder[LoggingEvent]])

    val filter = new ThresholdFilter
    val level = logConfig.getString("file.level")
    filter.setLevel(level)
    fileAppender.addFilter(filter.asInstanceOf[Filter[LoggingEvent]])
  }

  /** configure log file rolling policy. (Note assumes file append mode is true) */
  def configureRolling
      ( rollingFileAppender: RollingFileAppender[LoggingEvent],
        file:String,
        loggerContext: LoggerContext,
        logConfig:Config)
      : Unit = {

    val trigger = new SizeBasedTriggeringPolicy[LoggingEvent]
    val maxSize = logConfig.getString("file.max-size")
    trigger.setMaxFileSize(maxSize)
    trigger.setContext(loggerContext)
    trigger.start()
    rollingFileAppender.setTriggeringPolicy(trigger)

    val policy = new FixedWindowRollingPolicy
    policy.setContext(loggerContext)
    val maxFiles = logConfig.getInt("file.max-files")
    policy.setMaxIndex(maxFiles)
    policy.setMinIndex(1)
    val indexExt = file.lastIndexOf(".")
    val fnPattern = {
      indexExt match {
        case -1 =>
          // no file extension just add index to filename
          file + ".%i"
        case _ =>
          // put index before file extension so file type is not changed
          file.substring(0, indexExt + 1) + "%i." + file.substring(indexExt + 1)
      }
    }
    policy.setFileNamePattern(fnPattern)
    policy.setParent(rollingFileAppender)
    policy.start()

    rollingFileAppender.setRollingPolicy(policy)
  }

}
