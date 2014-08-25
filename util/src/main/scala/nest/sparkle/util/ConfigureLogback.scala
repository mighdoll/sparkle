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
import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.rolling._
import ch.qos.logback.core.{Appender, ConsoleAppender}

import com.typesafe.config.Config

import org.slf4j

/** configure a logback logger based on the config file */
object ConfigureLogback extends ConfigureLog with Log {

  /** configure logging based on the .conf file */
  private val configured = new AtomicBoolean(false)
  def configureLogging(sparkleConfig: Config): Unit = {
    if (configured.compareAndSet(false,true)) {
      slf4j.LoggerFactory.getLogger(slf4j.Logger.ROOT_LOGGER_NAME) match {
        case rootLogger: Logger => configureLogBack(sparkleConfig, rootLogger)
        case x                  => log.warn(s"unsupported logger, can't configure logging: ${x.getClass}")
      }
    }
  }

  /** configure file based logger for logback, based on settings in the .conf file */
  private def configureLogBack(config: Config, rootLogger: Logger): Unit = {
    val logConfig = config.getConfig("logging")
    val context = slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    context.reset()  // Remove any configuration from other libraries

//    println(s"logback logging to $file")
    val levels = logConfig.getConfig("levels")
    levels.entrySet().asScala.foreach { entry =>
      val logger = if (entry.getKey == "root") {
        rootLogger
      } else {
        slf4j.LoggerFactory.getLogger(entry.getKey).asInstanceOf[Logger]
      }
      val level = entry.getValue.unwrapped.toString      
      logger.setLevel(Level.toLevel(level))
    }

    // attach new file appender
    if (logConfig.getBoolean("file.enable")) {
      val fileAppender = new RollingFileAppender[LoggingEvent]
      fileAppender.setName("File")
      fileAppender.setContext(context)
      
      val file = logConfig.getString("file.path")
      fileAppender.setFile(file)
      
      val append = logConfig.getBoolean("file.append")
      fileAppender.setAppend(append)
      
      val encoder = new PatternLayoutEncoder
      encoder.setContext(context)
      val pattern = logConfig.getString("file.pattern")
      encoder.setPattern(pattern)
      encoder.start()
      fileAppender.setEncoder(encoder.asInstanceOf[Encoder[LoggingEvent]])
      
      val policy = new FixedWindowRollingPolicy
      //policy.setContext(context)
      val maxFiles = logConfig.getInt("file.max-files")
      policy.setMaxIndex(maxFiles)
      val fnPattern = file + ".%i"  // add ".zip" to compress
      //val ii = file.lastIndexOf(".")
      //val fnPattern = file.substring(0,ii) + "%i." + file.substring(ii+1)
      policy.setFileNamePattern(fnPattern)
      policy.setParent(fileAppender)
      fileAppender.setRollingPolicy(policy)
      
      val trigger = new SizeBasedTriggeringPolicy[LoggingEvent]
      val maxSize = logConfig.getString("file.max-size")
      trigger.setMaxFileSize(maxSize)
      //trigger.setContext(context)
      fileAppender.setTriggeringPolicy(trigger)
      
      val filter = new ThresholdFilter
      val level  = logConfig.getString("file.level")
      filter.setLevel(level)
      fileAppender.addFilter(filter.asInstanceOf[Filter[LoggingEvent]])
      
      fileAppender.start() 
      rootLogger.addAppender(fileAppender.asInstanceOf[Appender[ILoggingEvent]])
    }
    
    // Attach console appender is enabled.
    if (logConfig.getBoolean("console.enable")) {
      val consoleAppender = new ConsoleAppender[LoggingEvent]
      consoleAppender.setName("Console")
      consoleAppender.setContext(context)
      consoleAppender.setTarget("System.out")
      
      val encoder = new PatternLayoutEncoder
      encoder.setContext(context)
      val pattern = logConfig.getString("console.pattern")
      encoder.setPattern(pattern)
      encoder.start()
      consoleAppender.setEncoder(encoder.asInstanceOf[Encoder[LoggingEvent]])
      
      val filter = new ThresholdFilter
      val level  = logConfig.getString("console.level")
      filter.setLevel(level)
      filter.start()
      consoleAppender.addFilter(filter.asInstanceOf[Filter[LoggingEvent]])
      
      consoleAppender.start()
      rootLogger.addAppender(consoleAppender.asInstanceOf[Appender[ILoggingEvent]])
    }
  }

}
