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

package nest.sparkle.loader

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.Success
import scala.collection.JavaConverters._

import org.slf4j.LoggerFactory

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props

import spray.util._

import nest.sparkle.util.ConfigUtil
import nest.sparkle.store.cassandra.CassandraStore
import nest.sparkle.util.GuavaConverters._

class TestFilesLoader extends FunSuite with Matchers with BeforeAndAfterAll {
  val log = LoggerFactory.getLogger(classOf[TestFilesLoader])
  
  val config = {
    val source = ConfigFactory.load().getConfig("sparkle-time-server")
    ConfigUtil.modifiedConfig(
      source, 
      Some("sparkle-store-cassandra.keySpace","testfileloader")
    )
  }
  val storeConfig = config.getConfig("sparkle-store-cassandra")
  val contactHosts = storeConfig.getStringList("contactHosts").asScala.toSeq
  val keySpace = storeConfig.getString("keySpace")
  lazy val testDb = CassandraStore(config)
  
  implicit val system = ActorSystem("test-FilesLoader")
  import system.dispatcher
  
  override def beforeAll() {
    CassandraStore.dropKeySpace(contactHosts, keySpace)
  }
  
  override def afterAll() {
    testDb.close().toFuture.await(10.seconds)
    CassandraStore.dropKeySpace(contactHosts, keySpace)
  }

  /** return a future that completes when the loader reports that loading is complete */
  def onLoadComplete(path: String): Future[Unit] = {
    val promise = Promise[Unit]
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[LoadComplete])

    promise.future
  }

  test("load csv file") {
    val filePath = "sparkle/src/test/resources/epochs.csv"
    FilesLoader(filePath, testDb)
    onLoadComplete(filePath).await
    val column = testDb.column[Long, Double]("sparkle/src/test/resources/epochs.csv/count").await
    val read = column.readRange(None, None)
    val results = read.toBlockingObservable.toList
    results.length shouldBe 2751
  }
}

/** Constructor for a ReceiveLoaded actor */
object ReceiveLoaded {
  def props(targetPath: String, complete: Promise[Unit]): Props =
    Props(classOf[ReceiveLoaded], targetPath, complete)
}

/** An actor that completes a future when a LoadComplete message is received */
class ReceiveLoaded(targetPath: String, complete: Promise[Unit]) extends Actor {
  def receive = {
    case LoadComplete(path) if path == targetPath =>
      complete.complete(Success())
  }
}


