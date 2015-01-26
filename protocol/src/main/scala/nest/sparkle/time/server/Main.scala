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

import org.clapper.argot._
import org.clapper.argot.ArgotConverters._

import nest.sparkle.util.{ConfigUtil, SparkleApp}

/** Main launcher for Sparkle application */
object Main extends SparkleApp {
  override def appName = "sparkle"
  override def appVersion = "Version 0.6.0"  // TODO: get from the build

  val filesPath = parser.option[String](List("f", "files"), "path", ".csv/.tsv file, or directory containing .csv or .tsv files")
  val filesStrip = parser.option[Int](List("s", "files-strip"), "strip", "Number of leading path elements to strip off of path when creating DataSet name")
  val erase = parser.flag[Boolean](List("format"), "erase and format the database")
  val port = parser.option[Int](List("p", "port"), "port", "tcp port for web server")
  val root = parser.option[String](List("root"), "path", "directory containing custom web pages to serve")
  val display = parser.flag(List("display"), "navigate the desktop web browser to the current dashboard")
  
  initialize()

  val launch = SparkleAPIServer(rootConfig)

  display.value.foreach { _ => launch.launchDesktopBrowser() }
  
  override def overrides = {
    val sparkleConfigName = ConfigUtil.sparkleConfigName
    val portMapping = port.value.toList.map { (s"$sparkleConfigName.port", _) }
    val rootMapping = root.value.toList.map { value => (s"$sparkleConfigName.web-root.directory", List(value)) }
    val eraseOverride = erase.value.toList.map { (s"$sparkleConfigName.erase-store", _) }
    val strip = filesStrip.value.getOrElse(0)
    val filesOverride = filesPath.value.toList.flatMap { path =>
      (s"$sparkleConfigName.files-loader.directories", List(s"$path")) ::
      (s"$sparkleConfigName.files-loader.directory-strip", strip) ::
      (s"$sparkleConfigName.files-loader.auto-start", "true") :: Nil
    }
    portMapping ::: rootMapping ::: eraseOverride ::: filesOverride
  }
}

