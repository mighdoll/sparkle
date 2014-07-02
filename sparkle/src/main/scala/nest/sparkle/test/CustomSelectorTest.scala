package nest.sparkle.test

import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import scala.reflect.runtime.universe.typeTag

import org.scalatest.FunSuite

import akka.actor.ActorSystem

import spray.util.pimpFuture

import nest.sparkle.loader.{FileLoadComplete, FilesLoader, ReceiveLoaded}
import nest.sparkle.store.Store
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.time.protocol.{CustomSourceSelector, TestDataService}
import nest.sparkle.util.Resources

import scala.reflect.runtime.universe._

/** test jig for making custom selector tests */
trait CustomSelectorTest extends CassandraTestConfig {
  /** return a future that completes when the loader reports that loading is complete */
  def onLoadComplete(system: ActorSystem, path: String): Future[Unit] = {
    // TODO Get rid of this copy/pasted onLoadComplete (by moving files loader to stream loader notification)
    val promise = Promise[Unit]
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[FileLoadComplete])

    promise.future
  }

  /** run a test function after loading some leaf data into cassandra */
  def withLoadedPath[T](resource: String, relativePath: String)(fn: (Store, ActorSystem) => T): T = {
    val filePath = Resources.filePathString(resource)

    withTestDb { testDb =>
      withTestActors { implicit system =>
        val complete = onLoadComplete(system, relativePath)
        val loader = FilesLoader(sparkleConfig, filePath, testDb, 0)
        complete.await
        val result =
          try {
            fn(testDb, system)
          } finally {
            loader.close()
          }
        result
      }
    }
  }
  
  
  /** a TestDataService with some tweaks for leaves configuration, and
    * as a store (and actor system) provided by a constructor rather than inheritance
    * this is handy for testing with a store that's dynamically setup and torn down
    * in testing as the TestCassandraStore does.
    */
  class CustomSourceService[T <: CustomSourceSelector: TypeTag](override val store: Store, actorSystem: ActorSystem) extends FunSuite with TestDataService {
    // note: defs instead of vals to deal with initialization order
    def className = typeTag[T].tpe.typeSymbol.fullName
    override def actorRefFactory: ActorSystem = actorSystem
    def selectors = Seq(className).asJava

    override def configOverrides: List[(String, Any)] = super.configOverrides :+ (
      "sparkle-time-server.custom-selectors" -> selectors)
  }

}

