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
import com.typesafe.config.{Config, ConfigFactory}
import akka.actor._
import nest.sparkle.util.{ConfigUtil, ConfigureLogback}
import nest.sparkle.test.SparkleTestConfig
import scala.concurrent.Future
import nest.sparkle.loader.ReceiveLoaded
import scala.concurrent.Promise
import nest.sparkle.loader.FileLoadComplete
import nest.sparkle.util.Resources
import nest.sparkle.loader.FilesLoader
import nest.sparkle.store.Store
import spray.util._

/** a test jig for running tests using cassandra.
 *  (In the main project to make it easy to share between tests and integration tests) */
trait CassandraTestConfig extends SparkleTestConfig {
  /** subclasses should define their own keyspace so that tests don't interfere with each other  */
  def testKeySpace:String = getClass.getSimpleName

  override def configOverrides:List[(String,Any)] =
    super.configOverrides :+
    ("sparkle-time-server.sparkle-store-cassandra.key-space" -> testKeySpace)

  /** the 'sparkle' level Config, one down from the outermost */
  lazy val sparkleConfig:Config = {
    rootConfig.getConfig("sparkle-time-server")
  }

  /** recreate the database and a test column */
  def withTestDb[T](fn: CassandraReaderWriter => T): T = {
    val storeConfig = sparkleConfig.getConfig("sparkle-store-cassandra")
    val testContactHosts = storeConfig.getStringList("contact-hosts").asScala.toSeq
    CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
    val notification = new WriteNotification
    val store = CassandraStore.readerWriter(sparkleConfig, notification)

    try {
      fn (store)
    } finally {
      store.close()
//      CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
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
  def onLoadComplete(system: ActorSystem, path: String): Future[Unit] = {
    // TODO Get rid of this copy/pasted onLoadComplete (by moving files loader to stream loader notification)
    val promise = Promise[Unit]
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[FileLoadComplete])

    promise.future
  }

  /** run a test function after loading some data into cassandra */
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


}
