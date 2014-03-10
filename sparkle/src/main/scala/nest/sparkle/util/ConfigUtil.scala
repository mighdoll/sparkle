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
import com.typesafe.config.ConfigValueFactory
import scala.collection.JavaConverters._


/** utility functions for working with the typesafe Config library */
object ConfigUtil {
  /** apply some new key,value settings to a Config, returning the modified config */
  def modifiedConfig(config: Config, overrides: Option[(String, Any)]*): Config = {
    overrides.foldLeft(config){ (conf, keyValueOpt) =>
      keyValueOpt map {
        case (key, value) =>
          conf.withValue(key, ConfigValueFactory.fromAnyRef(value))
      } getOrElse config
    }
  }

  /** return a java.util.Properties from a config paragraph.  All keys in the 
   *  selected config paragraph are interpreted as strings */
  def properties(config: Config): java.util.Properties = {
    val entries =
      config.entrySet.asScala.map { entry =>
        val key = entry.getKey()
        val value = config.getString(key)
        (key, value)
      }
    val properties = new java.util.Properties()
    entries.foreach { case (key, value) => properties.put(key, value) }
    properties
  }

}
