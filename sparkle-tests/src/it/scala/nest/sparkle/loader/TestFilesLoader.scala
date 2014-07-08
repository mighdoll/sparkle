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

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import org.slf4j.LoggerFactory
import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import scala.concurrent.{ Future, Promise }
import spray.util._
import nest.sparkle.util.ConfigUtil
import nest.sparkle.store.cassandra.CassandraStore
import nest.sparkle.util.GuavaConverters._
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.util.Resources
import nest.sparkle.store.Event
import nest.sparkle.util.Log

class TestFilesLoader extends FunSuite with Matchers with CassandraTestConfig {
  val log = LoggerFactory.getLogger(classOf[TestFilesLoader])

  override def testKeySpace = "testfilesloader"

  /** return a future that completes when the loader reports that loading is complete */
  // TODO DRY this
  def onLoadCompleteOld(system: ActorSystem, path: String): Future[Unit] = {
    val promise = Promise[Unit]
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[LoadComplete])

    promise.future
  }

  /** try loading a known file and check the expected column for results */
  def testEpochsFile(resourcePath: String, columnPath: String, strip: Int = 0) {
    testLoadFile(resourcePath, columnPath, strip) { results: Seq[Event[Long, Double]] =>
      results.length shouldBe 2751
    }
  }

  /** try loading a known file and check the expected column for results */
  def testLoadFile[T, U, V](resourcePath: String, columnPath: String, strip: Int = 0)(fn: Seq[Event[U, V]] => T) {
    val filePath = Resources.filePathString(resourcePath)

    withTestDb { testDb =>
      withTestActors { implicit system =>
        import system.dispatcher
        val complete = onLoadCompleteOld(system, columnPath)
        FilesLoader(sparkleConfig, filePath, testDb, strip)
        complete.await

        val column = testDb.column[U, V](columnPath).await
        val read = column.readRange(None, None)
        val results = read.initial.toBlocking.toList
        fn(results)
      }
    }
  }

  test("load csv file") {
    testEpochsFile("epochs.csv", "epochs/count")
  }

  test("load csv file with leading underscore in filename") {
    testEpochsFile("_epochs.csv", "default/count")
  }

  test("load csv file with leading underscore in directory path element") {
    testEpochsFile("_ignore/epochs2.csv", "epochs2/count")
  }

  test("load csv file, skipping a directory path prefix") {
    testEpochsFile("skip/epochs.csv", "epochs/count", 1)
  }

  test("load csv file with boolean values, and second resolution timestamps") {
    testLoadFile("booleanSeconds.csv", "booleanSeconds/value") { results: Seq[Event[Long, Boolean]] =>
      results.map(_.value) shouldBe Seq(false, false, true, true)
      results.map(_.argument) shouldBe Seq(1, 2, 3, 4) // TODO should be 1000,2000,etc.
    }
  }

  test("load csv file with a string value") {
    testLoadFile("manyTypes.csv", "manyTypes/string") { results: Seq[Event[Long, String]] =>
      results.length shouldBe 1
      results.head.value shouldBe "fred"
    }
  }
  test("load csv file with an integer value") {
    testLoadFile("manyTypes.csv", "manyTypes/int") { results: Seq[Event[Long, Int]] =>
      results.head.value shouldBe -1
    }
  }
  test("load csv file with a long value") {
    testLoadFile("manyTypes.csv", "manyTypes/long") { results: Seq[Event[Long, Long]] =>
      results.head.value shouldBe 9876543210L
    }
  }
  test("load file with a comment and a blank line") {
    testLoadFile("comments.csv", "comments/b") { results: Seq[Event[Long, Long]] =>
      results.length shouldBe 2
      results.tail.head shouldBe Event(2L,2L)
    }
  }

}

