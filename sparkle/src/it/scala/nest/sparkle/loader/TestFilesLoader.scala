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
import org.scalatest.FunSuite
import org.scalatest.Matchers
import nest.sparkle.store.cassandra.CassandraStore
import org.scalatest.BeforeAndAfterAll
import akka.actor.ActorSystem
import spray.util._
import akka.actor.Actor
import akka.actor.Props
import nest.sparkle.store.Storage
import java.nio.file.Path
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Success
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TestFilesLoader extends FunSuite with Matchers with BeforeAndAfterAll {
  val log = LoggerFactory.getLogger(classOf[TestFilesLoader])
  lazy val testDb = {
    val store = CassandraStore("localhost")
    store.format(Some("testFilesLoader"))
    store
  }
  implicit val system = ActorSystem("test-FilesLoader")
  import system.dispatcher

  override def afterAll() {
    testDb.close()
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


