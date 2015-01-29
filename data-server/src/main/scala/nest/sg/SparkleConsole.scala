package nest.sg

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem

import nest.sparkle.store.Store

/** a collection of handy functions useful from the repl console */
object SparkleConsole extends SparkleConsole

/** a collection of handy functions useful from the repl console */
trait SparkleConsole
    extends ConsoleServer
    with StorageConsole
    with MeasurementConsole
    with PlotConsole
    with LoaderConsole {

  override implicit def execution: ExecutionContext = server.system.dispatcher
  override def store:Store = server.store
  override def system:ActorSystem = server.system

  override def close(): Unit = {
    super.close()
    shutdown()
  }

}
