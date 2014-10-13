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

package nest.sparkle.store.cassandra

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import com.typesafe.config.{ Config, ConfigFactory }
import akka.actor._
import nest.sparkle.util.ConfigUtil
import nest.sparkle.test.SparkleTestConfig
import scala.concurrent.Future
import nest.sparkle.loader.ReceiveLoaded
import scala.concurrent.Promise
import nest.sparkle.loader.FileLoadComplete
import nest.sparkle.util.Resources
import nest.sparkle.loader.FilesLoader
import nest.sparkle.store.Store
import spray.util._
import nest.sparkle.loader.LoadComplete
import nest.sparkle.store.Event

/** a test jig for running tests using cassandra.
  * (In the main project to make it easy to share between tests and integration tests)
  */
trait CassandraTestConfig extends SparkleTestConfig {
  /** subclasses should define their own keyspace so that tests don't interfere with each other  */
  def testKeySpace: String = getClass.getSimpleName

  override def configOverrides: List[(String, Any)] =
    super.configOverrides :+
    (s"${ConfigUtil.sparkleConfigName}.sparkle-store-cassandra.key-space" -> testKeySpace)

  /** the 'sparkle' level Config */
  lazy val sparkleConfig:Config = {
    ConfigUtil.configForSparkle(rootConfig)
  }

  /** recreate the database and a test column */
  def withTestDb[T](fn: CassandraReaderWriter => T): T = {
    val storeConfig = sparkleConfig.getConfig("sparkle-store-cassandra")
    val testContactHosts = storeConfig.getStringList("contact-hosts").asScala.toSeq
    CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
    val notification = new WriteNotification
    val store = CassandraStore.readerWriter(sparkleConfig, notification)

    try {
      fn(store)
    } finally {
      store.close()
      //      CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
    }
  }

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

  /** run a function within a test actor system */
  def withTestActors[T](fn: ActorSystem => T): T = {
    val system = ActorSystem("test-config")
    try {
      fn(system)
    } finally {
      system.shutdown()
    }
  }

  /** return a future that completes when the loader reports that loading is complete */
  // TODO DRY this by moving files loader to stream loader style notification
  def onFileLoadComplete(system: ActorSystem, path: String): Future[Unit] = {
    val promise = Promise[Unit]
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[FileLoadComplete])

    promise.future
  }

  /** return a future that completes when the loader reports that loading is complete */
  // TODO DRY this by moving files loader to stream loader style notification
  def onLoadCompleteOld(system: ActorSystem, path: String): Future[Unit] = {
    val promise = Promise[Unit]
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[LoadComplete])

    promise.future
  }

  /** Run a test function after loading some data into cassandra.
    * @param fn - test function to call after the data has been loaded.
    * @param resource - directory in the classpath resources to load (recursively)
    * @param relativePath - call the function after a particular file in the resource directory has been
    *           completely loaded.
    * TODO have the FilesLoader report when the entire resource subdirectory is loaded, so
    * we don't need to path both the resource and relativePath. (Perhaps
    * the existing dataSet notification is enough for this?)
    */
  def withLoadedFileInResource[T](resource: String, relativePath: String) // format: OFF
      (fn: (Store, ActorSystem) => T): T = { // format: ON

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

  /** run a test function after loading some data into cassandra */
  def withLoadedFile[T](resourcePath: String) // format: OFF
      (fn: (Store, ActorSystem) => T): T = { // format: ON
    withLoadedFileInResource(resourcePath, resourcePath)(fn)  // TODO this seems incorrect..
  }

}

