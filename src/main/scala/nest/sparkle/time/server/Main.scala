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
import scala.concurrent.duration._
import spray.util._
import java.nio.file.Paths
import java.nio.file.Files
import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import java.awt.Desktop
import java.net.URI
import nest.sparkle.util.RepeatingRequest
import scala.util.Success
import scala.util.Failure
import nest.sparkle.store.cassandra.FileSystemStorage
import nest.sparkle.legacy.FileLoadedDataSet
import nest.sparkle.legacy.DirectoryDataRegistry
import nest.sparkle.util.Opt._
import akka.pattern.ask
import nest.sparkle.store.Storage
import nest.sparkle.util.Exceptions._
import nest.sparkle.loader.FilesLoader
import nest.sparkle.legacy.PreloadedRegistry

object Main extends App {
  val parser = new ArgotParser("sg", preUsage = Some("Version 0.4.4-SNAPSHOT"))

  val filesPath = parser.option[String](List("f", "files"), "path", ".csv/.tsv file, or directory containing .csv or .tsv files")
  val help = parser.flag[Boolean](List("h", "help"), "show this help")
  val port = parser.option[Int](List("p", "port"), "port", "tcp port for web server")
  val root = parser.option[String](List("root"), "path", "directory containing custom web pages to serve")
  val display = parser.flag(List("display"), "navigate the desktop web browser to the current dashboard")
  val debug = parser.flag(List("d", "debug"), "turn on debug logging")

  try {
    parser.parse(args)

    help.value.foreach { v =>
      Console.println(parser.usageString())
      sys.exit(0)
    }

    val config = ConfigServer.loadConfig(debugLogging = debug.value.isDefined)
    implicit val system = ActorSystem("sparkle-graph", config)

    val webPort = port.value.getOrElse(1234)
    val storage = Storage.instantiateStorage(config)
    filesPath.value.map { pathString =>
      val writeableStorage = Storage.instantiateWritableStorage(config)
      FilesLoader(pathString, writeableStorage)
    }

    // TODO implement some kind of dataRegistry w/cassandra
    val fixmeRegistry = PreloadedRegistry(Nil)(system.dispatcher)
    val service = system.actorOf(Props(
      new ConfiguredDataServer(fixmeRegistry, storage, config, root.value)),
      "sparkle-server"
    )
    startServer(service, webPort)

    display.value.foreach { _ =>
      val uri = new URI(s"http://localhost:$webPort/")
      import system.dispatcher
      RepeatingRequest.get(uri + "health").onComplete {
        case Success(_) =>
          val desktop = Desktop.getDesktop()
          desktop.browse(uri)
        case Failure(err) =>
          Console.err.println(s"failed to launch server: ${err.getMessage}")
          sys.exit(1)
      }
    }

  } catch {
    case e: ArgotUsageException =>
      println(e.message)
      sys.exit(1)
  }

  /** return the elements in a string containing a comma separated list */
  def csvList(opt: SingleValueOption[String]): List[String] =
    opt.value.toList.flatMap(columns => columns.split(",")).map(_.trim)

  def startServer(serviceActor: ActorRef, port: Int)(implicit system: ActorSystem) {
    implicit val timeout = Timeout(10.seconds)
    val started = IO(Http) ? Http.Bind(serviceActor, interface = "0.0.0.0", port = port)
    started.await // wait until server is started
  }

}







