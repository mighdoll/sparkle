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

import com.typesafe.config.Config
import org.apache.log4j.RollingFileAppender
import org.apache.log4j.PatternLayout
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.ConsoleAppender
import scala.collection.JavaConverters._

/** configure log4j logging based on a .conf file */
object ConfigureLog4j {

  /** configure log4j logging based on a .conf file */
  def configure(config: Config): Unit = {
    val log4jConfig = config.getConfig("log4j")

    val levels = log4jConfig.getConfig("levels")
    levels.entrySet().asScala.map { entry =>
      val logger = Logger.getLogger(entry.getKey)
      val level = entry.getValue.unwrapped.toString
      logger.setLevel(Level.toLevel(level))
    }

    // All appenders use the same pattern.
    val pattern = log4jConfig.getString("pattern")
    val patternLayout = new PatternLayout(pattern)

    if (log4jConfig.getBoolean("file.use")) {
      val fileAppender = new RollingFileAppender()
      fileAppender.setName("FileLogger")
      fileAppender.setLayout(patternLayout)
      fileAppender.setThreshold(Level.DEBUG)
      
      val file = log4jConfig.getString("file.path")
      fileAppender.setFile(file)
      
      val maxSize = log4jConfig.getString("file.max-size")
      fileAppender.setMaxFileSize(maxSize)
      
      val maxFiles = log4jConfig.getInt("file.max-files")
      fileAppender.setMaxBackupIndex(maxFiles)
      
      val append = log4jConfig.getBoolean("file.append")
      fileAppender.setAppend(append)
      
      fileAppender.activateOptions()
      Logger.getRootLogger.addAppender(fileAppender)
    }

    if (log4jConfig.getBoolean("console.use")) {
      val consoleAppender = new ConsoleAppender()
      consoleAppender.setName("Console")
      consoleAppender.setLayout(patternLayout)
      consoleAppender.setThreshold(Level.WARN)
      consoleAppender.activateOptions()
      
      Logger.getRootLogger.addAppender(consoleAppender)
    }

  }

}
