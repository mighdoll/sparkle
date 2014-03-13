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
import akka.actor.{ ActorRef, Props }
import akka.util.Timeout
import java.nio.file.Paths
import java.nio.file.Files
import akka.actor.ActorSystem
import nest.sparkle.store.cassandra.FileSystemStore
import nest.sparkle.legacy.FileLoadedDataSet
import nest.sparkle.legacy.DirectoryDataRegistry
import nest.sparkle.util.Opt._
import nest.sparkle.store.Store
import nest.sparkle.util.Exceptions._
import nest.sparkle.loader.FilesLoader
import nest.sparkle.legacy.PreloadedRegistry
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.Config
import nest.sparkle.util.ArgotApp
import nest.sparkle.util.ConfigUtil.optionModifiedConfig

/** Main launcher for Sparkle application */
object Main extends ArgotApp {
  val parser = new ArgotParser("sg", preUsage = Some("Version 0.4.4-SNAPSHOT")) // TODO get version from the build

  val filesPath = parser.option[String](List("f", "files"), "path", ".csv/.tsv file, or directory containing .csv or .tsv files")
  val help = parser.flag[Boolean](List("h", "help"), "show this help")
  val erase = parser.flag[Boolean](List("format"), "erase and format the database")
  val port = parser.option[Int](List("p", "port"), "port", "tcp port for web server")
  val root = parser.option[String](List("root"), "path", "directory containing custom web pages to serve")
  val display = parser.flag(List("display"), "navigate the desktop web browser to the current dashboard")

  app(parser, help) {
    val loadedConfig = ConfigServer.loadConfig()
    implicit val system = ActorSystem("sparkle-graph", loadedConfig)

    val portMapping = port.value.map { ("port", _) }
    val rootMapping = root.value.map { ("web-root", _) }
    val mappings = portMapping :: rootMapping :: Nil
    val config = optionModifiedConfig(loadedConfig, mappings: _*)

    val launch = new ServerLaunch(config)
    
    filesPath.value.foreach { pathString =>
      FilesLoader(pathString, launch.writeableStore)
    }

    display.value.foreach { _ => launch.launchDesktopBrowser() }
  } 
}







