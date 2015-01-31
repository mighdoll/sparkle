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

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING

import scala.collection.JavaConverters.{asJavaIterableConverter, asScalaBufferConverter, asScalaSetConverter}

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

/** utility functions for working with the typesafe Config library */
object ConfigUtil {

  /** Load the configuration from a .conf file in the filesystem, falling back to
    * the built in reference.conf.
    */
  def configFromFile(configFiles: String*): Config = {
    configFromFilesAndResources(configFiles, Seq())
  }

  /** Load the configuration from a .conf file in the filesystem, falling back to
    * provided resources files, and then the built in reference.conf.
    */
  def configFromFilesAndResources(files: Seq[String], resources:Seq[String]): Config = {
    val baseConfig = ConfigFactory.load()
    val fileConfigs = files.map { configFile =>
        val file = new File(configFile)
        ConfigFactory.parseFile(file)
      }
    val resourceConfigs = resources map ConfigFactory.parseResources
    val allConfigs:Seq[Config] = (fileConfigs ++ resourceConfigs :+ baseConfig)
    val combined = allConfigs.reduceLeft{ (a,b) => b.withFallback(a) }
    combined.resolve()
  }

  /** apply some new key,value settings to a Config, returning the modified config */
  def optionModifiedConfig(config: Config, overrides: Option[(String, Any)]*): Config = {
    overrides.foldLeft(config){ (conf, keyValueOpt) =>
      keyValueOpt map modify(conf) getOrElse config
    }
  }

  /** apply some new key,value settings to a Config, returning the modified config */
  def modifiedConfig(config: Config, overrides: (String, Any)*): Config = {
    overrides.foldLeft(config){ (conf, keyValue) =>
      modify(conf).apply(keyValue)
    }
  }

  /** return a partial function that modifies a Config with a key, value pair */
  private def modify(config: Config): PartialFunction[(String, Any), Config] = {
    case (key: String, values: Iterable[_]) =>
      config.withValue(key, ConfigValueFactory.fromIterable(values.asJava))
    case (key: String, value: Any) =>
      config.withValue(key, ConfigValueFactory.fromAnyRef(value))
  }

  /** return a java.util.Properties from a config paragraph.  All keys in the
    * selected config paragraph are interpreted as strings
    */
  def properties(config: Config): java.util.Properties = {
    val entries =
      config.entrySet.asScala.map { entry =>
        val key = entry.getKey
        val value = config.getString(key)
        (key, value)
      }
    val properties = new java.util.Properties()
    entries.foreach { case (key, value) => properties.put(key, value) }
    properties
  }
  
  /** Given the root config return the sparkle config */
  def configForSparkle(rootConfig: Config): Config = {
    rootConfig.getConfig(sparkleConfigName)
  }
  
  val sparkleConfigName = "sparkle"

  
  /** write a composited version of the .conf file */
  def dumpConfigToFile(config: Config) {
    val writeList = config.getStringList(s"$sparkleConfigName.dump-config").asScala
    writeList.headOption.foreach { fileName =>
      import java.nio.file.StandardOpenOption._
      val configString = config.root.render()
      val charSet = Charset.forName("UTF-8")      
      val writer = Files.newBufferedWriter(Paths.get(fileName), charSet, TRUNCATE_EXISTING, CREATE)
      writer.write(configString)
      writer.close()
    }
  }

}
