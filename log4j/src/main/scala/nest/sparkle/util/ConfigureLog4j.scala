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
import org.apache.log4j.{ ConsoleAppender, Level, Logger, PatternLayout, RollingFileAppender }
import com.typesafe.config.Config

/** configure log4j logging based on a .conf file */
object ConfigureLog4j extends ConfigureLog {

  /** configure logging based on the .conf file */
  def configureLogging(sparkleConfig: Config): Unit = {
    configure(sparkleConfig)
  }

  /** temporarily set the log level for a logger, e.g. for a noisy unit test*/
  def withLogLevel[T](loggerName:String, level:String)(fn: =>T):T = {
    val logger = Logger.getLogger(loggerName)
    val origLevel = logger.getLevel()
    try {
      val newLevel = Level.toLevel(level)
      logger.setLevel(newLevel)
      fn
    } finally {
      logger.setLevel(origLevel)
    }
  }

  /** configure log4j logging based on a .conf file
    * @param sparkleConfig sparkle config object
    */
  private def configure(sparkleConfig: Config): Unit = {
    val logConfig = sparkleConfig.getConfig("logging")
    val rootLogger = Logger.getRootLogger
    // Clear whatever Kafka, etc. add
    rootLogger.getLoggerRepository.resetConfiguration()

    val levels = logConfig.getConfig("levels")
    levels.entrySet().asScala.map { entry =>
    val key = entry.getKey.stripPrefix("\"").stripSuffix("\"")
      val logger =
        if (key == "root") {
          rootLogger
        } else {
          Logger.getLogger(key)
        }
      val level = entry.getValue.unwrapped.toString
      println(s"log4j configuring $key -> $level")
      logger.setLevel(Level.toLevel(level))
    }

    if (logConfig.getBoolean("file.enable")) {
      val fileAppender = new RollingFileAppender()
      fileAppender.setName("FileLogger")

      val pattern = logConfig.getString("file.pattern")
      val patternLayout = new PatternLayout(pattern)
      fileAppender.setLayout(patternLayout)

      val level = logConfig.getString("file.level")
      fileAppender.setThreshold(Level.toLevel(level))

      val file = logConfig.getString("file.path")
      fileAppender.setFile(file)

      val maxSize = logConfig.getString("file.max-size")
      fileAppender.setMaxFileSize(maxSize)

      val maxFiles = logConfig.getInt("file.max-files")
      fileAppender.setMaxBackupIndex(maxFiles)

      val append = logConfig.getBoolean("file.append")
      fileAppender.setAppend(append)

      fileAppender.activateOptions()
      rootLogger.addAppender(fileAppender)
    }

    if (logConfig.getBoolean("console.enable")) {
      val consoleAppender = new ConsoleAppender()
      consoleAppender.setName("Console")

      val pattern = logConfig.getString("console.pattern")
      val patternLayout = new PatternLayout("L4J: " + pattern)
      consoleAppender.setLayout(patternLayout)

      val level = logConfig.getString("console.level")
      consoleAppender.setThreshold(Level.toLevel(level))

      consoleAppender.activateOptions()

      rootLogger.addAppender(consoleAppender)
    }

    Logger.getLogger(getClass).log(Level.INFO, "started log4j logging")
  }

}
