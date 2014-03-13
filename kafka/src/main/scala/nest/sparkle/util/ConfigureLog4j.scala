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
import org.apache.log4j.FileAppender
import org.apache.log4j.PatternLayout
import org.apache.log4j.Level
import org.apache.log4j.Logger

/** configure log4j logging based on a .conf file */
object ConfigureLog4j {
  
  /** configure log4j logging based on a .conf file */
  def configure(config: Config) {    
    val log4jConfig = config.getConfig("log4j")
    val file = log4jConfig.getString("file")
    val append = log4jConfig.getBoolean("append")
    val pattern = log4jConfig.getString("pattern")
    
    val fileAppender = new FileAppender()    
    fileAppender.setName("FileLogger")
    fileAppender.setFile(file)
    fileAppender.setLayout(new PatternLayout(pattern))
    fileAppender.setThreshold(Level.DEBUG)
    fileAppender.setAppend(append)
    fileAppender.activateOptions()

    Logger.getRootLogger().addAppender(fileAppender)
  }

}