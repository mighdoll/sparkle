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

package nest.sparkle.time.server

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.Config
import nest.sparkle.legacy.PreloadedRegistry
import akka.actor.Props
import nest.sparkle.store.Store
import akka.io.IO
import scala.concurrent.duration._
import spray.can.Http
import akka.pattern.ask
import akka.actor.ActorRef
import spray.util._
import nest.sparkle.util.RepeatingRequest
import scala.util.Success
import scala.util.Failure
import java.awt.Desktop
import java.net.URI

class ServerLaunch(config: Config)(implicit system: ActorSystem) {
  val store = Store.instantiateStore(config)
  val webPort = config.getInt("port")
  lazy val writeableStore = Store.instantiateWritableStore(config)

  // TODO implement some kind of dataRegistry w/cassandra
  val fixmeRegistry = PreloadedRegistry(Nil)(system.dispatcher)
  val service = system.actorOf(Props(
    new ConfiguredDataServer(fixmeRegistry, store, config)),
    "sparkle-server"
  )
  startServer(service, webPort)

  def startServer(serviceActor: ActorRef, port: Int)(implicit system: ActorSystem) {
    implicit val timeout = Timeout(10.seconds)
    val started = IO(Http) ? Http.Bind(serviceActor, interface = "0.0.0.0", port = port)
    started.await // wait until server is started
  }

  def launchDesktopBrowser() {
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

}
