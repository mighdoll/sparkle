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
import nest.sparkle.util.ConfigureLogback.configureLogging

case class RootAndSparkleConfig(val root:Config, val sparkle:Config)

/** utilities for setting up configuration for a sparkle server */
object ConfigureSparkle extends Log {
  /** Load the configuration from the application.conf and reference.conf resources.
    */
  def loadConfig(configResource: String = "application.conf"): Config = {
    val root = ConfigFactory.load(configResource)
    log.info(s"using config resource: $configResource")
    localConfig(root)
  }

  /** point at the sparkle-time-server section of the config file. And
   *  take advantage of the now-known config settings to setup logging. */
  private def localConfig(root: Config): Config = {
    val local = root.getConfig("sparkle-time-server")
    configureLogging(local)
    local
  }


}
