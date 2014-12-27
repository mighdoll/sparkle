package nest.sparkle.store.cassandra

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

import akka.actor._
import spray.util._

import nest.sparkle.loader.{FileLoadComplete, FilesLoader, LoadComplete, ReceiveLoaded}
import nest.sparkle.store.{Event, Store}
import nest.sparkle.util.Resources

/** a test jig for running tests using cassandra.
  * (In the main project to make it easy to share between tests and integration tests) */
trait CassandraTestConfig 
  extends CassandraStoreTestConfig
{

  /** try loading a known file and check the expected column for results */
  def testLoadFile[T, U, V](resourcePath: String, columnPath: String)(fn: Seq[Event[U, V]] => T) {
    val filePath = Resources.filePathString(resourcePath)

    withTestDb { testDb =>
      withTestActors { implicit system =>
        import system.dispatcher
        val complete = onLoadCompleteOld(system, columnPath)
        FilesLoader(sparkleConfig, filePath, testDb, 0)
        complete.await(4.seconds)

        val column = testDb.column[U, V](columnPath).await
        val read = column.readRange(None, None)
        val results = read.initial.toBlocking.toList
        fn(results)
      }
    }
  }

  /** return a future that completes when the loader reports that loading is complete */
  // TODO DRY this by moving files loader to stream loader style notification
  def onLoadCompleteOld(system: ActorSystem, path: String): Future[Unit] = {
    val promise = Promise[Unit]()
    system.eventStream.subscribe(
      system.actorOf(ReceiveLoaded.props(path, promise)), classOf[LoadComplete]
    )

    promise.future
  }

  /** run a test function after loading some data into cassandra */
  def withLoadedFile[T](resourcePath: String) // format: OFF
      (fn: (Store, ActorSystem) => T): T =
  {
    // format: ON
    withLoadedFileInResource(resourcePath, resourcePath)(fn) // TODO this seems incorrect..
  }

  /** Run a test function after loading some data into cassandra.
    * @param fn - test function to call after the data has been loaded.
    * @param resource - directory in the classpath resources to load (recursively)
    * @param relativePath - call the function after a particular file in the resource directory has been
    *                     completely loaded.
    *                     TODO have the FilesLoader report when the entire resource subdirectory is loaded, so
    *                     we don't need to path both the resource and relativePath. (Perhaps
    *                     the existing dataSet notification is enough for this?) */
  def withLoadedFileInResource[T](resource: String, relativePath: String) // format: OFF
      (fn: (Store, ActorSystem) => T): T =
  {
    // format: ON

    withTestDb { testDb =>
      withTestActors { implicit system =>
        val complete = onFileLoadComplete(system, relativePath)
        val loadPath = Resources.filePathString(resource)
        val loader = FilesLoader(sparkleConfig, loadPath, testDb, 0)
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

  /** return a future that completes when the loader reports that loading is complete */
  // TODO DRY this by moving files loader to stream loader style notification
  def onFileLoadComplete(system: ActorSystem, path: String): Future[Unit] = {
    val promise = Promise[Unit]()
    system.eventStream.subscribe(
      system.actorOf(ReceiveLoaded.props(path, promise)), classOf[FileLoadComplete]
    )

    promise.future
  }

}

