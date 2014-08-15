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

import java.awt.Desktop
import java.net.URI
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }
import org.slf4j.LoggerFactory
import com.typesafe.config.Config
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.util._
import com.codahale.metrics.Slf4jReporter
import nest.sparkle.legacy.PreloadedRegistry
import nest.sparkle.loader.{ FilesLoader, LoadPathDoesNotExist }
import nest.sparkle.store.Store
import nest.sparkle.store.cassandra.WriteNotification
import nest.sparkle.util.{ Log, RepeatingRequest }
import nest.sparkle.util.ConfigUtil
import nest.sparkle.util.ConfigUtil.modifiedConfig
import nest.sparkle.util.ConfigureLogback.configureLogging
import nest.sparkle.util.MetricsInstrumentation
import nest.sparkle.util.InitializeReflection

protected class ServerLaunch(val rootConfig: Config)(implicit val system: ActorSystem) extends Log {
  val sparkleConfig = rootConfig.getConfig("sparkle-time-server")
  val notification = new WriteNotification()
  val store = Store.instantiateStore(sparkleConfig, notification)
  lazy val webPort = sparkleConfig.getInt("port")
  lazy val writeableStore = Store.instantiateWritableStore(sparkleConfig, notification)
  
  def actorSystem = system

  val fixmeRegistry = PreloadedRegistry(Nil)(system.dispatcher) // TODO delete this once we drop v0 protocol
  val service = system.actorOf(Props(
    new ConfiguredDataServer(fixmeRegistry, store, rootConfig)),
    "sparkle-server"
  )
  
  AdminService.start(rootConfig, store).await(10.seconds)

  /* Note that nothing may be specified to be auto-start but we started an
   * actor system so the main process will not end. This will disappear when
   * each component gets their own Main/ServerLaunch.
   */
  possiblyErase()
  possiblyStartFilesLoader()
  startServer(service, webPort)
  // TODO: Make web socket start optional
  val webSocket = new DataWebSocket(store, rootConfig)
  

  /** (for desktop use) Open the web browser to the sparkle http server.
    */
  def launchDesktopBrowser(path: String = ""): Unit = {
    val uri = new URI(s"http://localhost:$webPort/$path")
    println(s"browsing to: $uri")
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
  
  def shutdown() {
    actorSystem.shutdown()
  }

  /** Launch the http server for sparkle API requests.
    *
    * This call will block until the server is ready to accept incoming requests.
    */
  private def startServer(serviceActor: ActorRef, port: Int)(implicit system: ActorSystem): Unit = {
    if (sparkleConfig.getBoolean("auto-start")) {
      implicit val timeout = Timeout(10.seconds)
      val started = IO(Http) ? Http.Bind(serviceActor, interface = "0.0.0.0", port = port)
      started.await // wait until server is started
    }
  }

  /** Erase and reformat the storage system if requested */
  private def possiblyErase(): Unit = {
    if (sparkleConfig.getBoolean("erase-store")) {
      writeableStore.format()
    }
  }

  /** launch a FilesLoader for each configured directory */
  private def possiblyStartFilesLoader(): Unit = {
    if (sparkleConfig.getBoolean("files-loader.auto-start")) {
      val strip = sparkleConfig.getInt("files-loader.directory-strip")
      sparkleConfig.getStringList("files-loader.directories").asScala.foreach { pathString =>
        try {
          FilesLoader(sparkleConfig, pathString, writeableStore, strip)
        } catch {
          case LoadPathDoesNotExist(path) => sys.exit(1)
        }
      }
    }
  }
}

object ServerLaunch extends Log {
  /** convenience wrapper for creating a ServerLaunch object from command line arguments
    * optionally specifying a .conf file and optionally specifying overrides to the configuration
    */
  def apply(configFile: Option[String], configOverrides: (String, Any)*): ServerLaunch = {
    println(s"ServerLaunch.configFile: $configFile")
    val overriddenConfig = {
      val config = ConfigUtil.configFromFile(configFile)
      // TODO log if we're not using a --conf file too
      modifiedConfig(config, configOverrides: _*)
    }
    val sparkleConfig = overriddenConfig.getConfig("sparkle-time-server")
    configureLogging(sparkleConfig)
    log.info("---- starting server ----")

    InitializeReflection.init

    implicit val system = ActorSystem("sparkle", sparkleConfig)

    new ServerLaunch(overriddenConfig)
  }
}
