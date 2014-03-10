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

import org.slf4j

import com.typesafe.config.Config

import ch.qos.logback.classic.{Logger, LoggerContext}
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.{ILoggingEvent, LoggingEvent}
import ch.qos.logback.core.{Appender, FileAppender}
import ch.qos.logback.core.encoder.Encoder

/** configure a log4j logger based on the config file */
object LogConfiguration extends Log {

  /** configure logging based on the .conf file */
  def configureLogging(config: Config) {
    slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) match {
      case rootLogger: Logger => configureLogBack(config, rootLogger)
      case x                  => log.warn(s"unsupported logger, can't configure logging: ${x.getClass}")
    }
  }

  /** configure file based logger for logback, based on the .conf file */
  private def configureLogBack(config: Config, rootLogger: Logger) {
    val context = slf4j.LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    val logConfig = config.getConfig("log")
    val file = logConfig.getString("file")
    val pattern = logConfig.getString("pattern")
    val append = logConfig.getBoolean("append")

    // attach new file appender 
    val fileAppender = new FileAppender[LoggingEvent]
    fileAppender.setFile(file)
    val encoder = new PatternLayoutEncoder
    encoder.setPattern(pattern)
    encoder.setContext(context)
    encoder.start()
    fileAppender.setEncoder(encoder.asInstanceOf[Encoder[LoggingEvent]])
    fileAppender.setContext(context)
    fileAppender.start()
    rootLogger.addAppender(fileAppender.asInstanceOf[Appender[ILoggingEvent]])
  }
}
