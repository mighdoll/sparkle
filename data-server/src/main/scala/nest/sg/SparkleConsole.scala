package nest.sg

import scala.concurrent.ExecutionContext

import nest.sparkle.store.Store

/** a collection of handy functions useful from the repl console */
object SparkleConsole
  extends StorageConsole
  with MeasurementConsole
  with PlotConsole
  with ConsoleServer {

  override implicit def execution: ExecutionContext = server.system.dispatcher
  override def store:Store = server.store

}
