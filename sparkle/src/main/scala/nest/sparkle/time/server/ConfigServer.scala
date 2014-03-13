/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.time.server

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}
import nest.sparkle.util.Log
import nest.sparkle.util.LogConfiguration.configureLogging

object ConfigServer extends Log {
  /** Load the configuration from the application.conf and reference.conf resources.
    */
  def loadConfig(configResource: String = "application.conf"): Config = {
    val root = ConfigFactory.load(configResource)
    log.info(s"using config resource: $configResource")
    localConfig(root)
  }

  /** Load the configuration from a .conf file in the filesystem, falling back to
    * the built in reference.conf.
    */
  def loadConfigFromFile(configFileOpt: Option[String]): Config = {
    val baseConfig = ConfigFactory.load()
    var root =
      configFileOpt.map { configFile =>
        log.info(s"using config file: $configFile")
        val file = new File(configFile)
        val config = ConfigFactory.parseFile(file).resolve()
        config.withFallback(baseConfig)
      } getOrElse {
        baseConfig
      }
    localConfig(root)
  }

  /** point at the sparkle-time-server section of the config file, and optionally override debug settings */
  private def localConfig(root: Config): Config = {
    val local = root.getConfig("sparkle-time-server")
    configureLogging(local)
    local
  }


}
