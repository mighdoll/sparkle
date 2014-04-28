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

import akka.actor.ActorSystem

import nest.sparkle.util.ArgotApp
import nest.sparkle.util.ConfigUtil.optionModifiedConfig

/** Main launcher for Sparkle application */
object Main extends ArgotApp {
  val parser = new ArgotParser("sg", preUsage = Some("Version 0.4.4-SNAPSHOT")) // TODO get version from the build

  val filesPath = parser.option[String](List("f", "files"), "path", ".csv/.tsv file, or directory containing .csv or .tsv files")
  val filesStrip = parser.option[Int](List("s", "files-strip"), "strip", "Number of leading path elements to strip off of path when creating DataSet name")
  val help = parser.flag[Boolean](List("h", "help"), "show this help")
  val erase = parser.flag[Boolean](List("format"), "erase and format the database")
  val port = parser.option[Int](List("p", "port"), "port", "tcp port for web server")
  val confFile = parser.option[String](List("conf"), "path", "path to an application.conf file")
  val root = parser.option[String](List("root"), "path", "directory containing custom web pages to serve")
  val display = parser.flag(List("display"), "navigate the desktop web browser to the current dashboard")

  app(parser, help) {
    val portMapping = port.value.toList.map { ("sparke-time-server.port", _) }
    val rootMapping = root.value.toList.map { ("sparkle-time-server.web-root", _) }
    val eraseOverride = erase.value.toList.map { ("sparkle-time-server.erase-store", _) }
    val strip = filesStrip.value.getOrElse(0)
    val filesOverride = filesPath.value.toList.flatMap { path =>
      ("sparkle-time-server.files-loader.directories", List(s"$path")) ::
      ("sparkle-time-server.files-loader.directory-strip", strip) ::
      ("sparkle-time-server.files-loader.auto-start", "true") :: Nil
    }
    val configOverrides = portMapping ::: rootMapping ::: eraseOverride ::: filesOverride

    val launch = ServerLaunch(confFile.value, configOverrides:_*)

    display.value.foreach { _ => launch.launchDesktopBrowser() }
  }
}







