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

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{ FileVisitResult, Files, Path, Paths, SimpleFileVisitor }
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

import scala.concurrent.{ Future, Promise }

import org.scalatest.{ BeforeAndAfterAll, FunSuite, Matchers }
import org.slf4j.LoggerFactory

import akka.actor._
import akka.util.Timeout.longToTimeout
import spray.util._

import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.tools.Exporter
import nest.sparkle.util.Resources

class TestExporter extends FunSuite with CassandraTestConfig with Matchers with BeforeAndAfterAll {
  val log = LoggerFactory.getLogger(classOf[TestExporter])

  override def testKeySpace = "testexporter"

  override def configOverrides =
    super.configOverrides ++
      List(
        "exporter.timeout" -> "10s",
        "exporter.output" -> "/tmp/testexporter"
      )

  /** return a future that completes when the loader reports that loading is complete */
  // TODO DRY this
  def onLoadCompleteOld(system: ActorSystem, path: String): Future[Unit] = {
    val promise = Promise[Unit]()
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[LoadComplete])

    promise.future
  }

  ignore("export tsv file") {
    withTestDb { testDb =>
      withTestActors { implicit system =>
        // First load some data
        val filePath = Resources.filePathString("epochs.csv")
        val dataSet = "epochs"
        val complete = onLoadCompleteOld(system, dataSet)
        FilesLoader(sparkleConfig, filePath, testDb)
        complete.await

        val output = Paths.get(rootConfig.getString("exporter.output"))
        output match {
          case p if Files.isDirectory(p) => cleanDirectory(p)
          case f if Files.exists(f)      => throw new RuntimeException(s"${output.toString} is a file")
          case _                         => Files.createDirectory(output)
        }

        val timeout = rootConfig.getDuration("exporter.timeout", TimeUnit.MILLISECONDS)
        val exporter = Exporter(rootConfig)
        try {
          exporter.processDataSet(dataSet).await(timeout)

          val dataSetPath = output.resolve(dataSet)
          Files.exists(dataSetPath.resolve("_count.tsv")) shouldBe true
          Files.exists(dataSetPath.resolve("_p90.tsv")) shouldBe true
          Files.exists(dataSetPath.resolve("_p99.tsv")) shouldBe true

          val lines = Files.readAllLines(dataSetPath.resolve("_count.tsv"), StandardCharsets.UTF_8)
          lines.size shouldBe 2752
          lines.get(0) shouldBe "time\tcount"
          lines.get(2751) shouldBe "1357713357000\t570"
        } finally {
          exporter.close()
        }
      }
    }
  }

  /** Remove all the files in a directory recursively.
    * @param path Directory to clean.
    */
  private def cleanDirectory(path: Path) {
    if (Files.isDirectory(path)) {
      Files.walkFileTree(path, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }
        override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {
          e match {
            case _: IOException => throw e
            case _ =>
              Files.delete(dir)
              FileVisitResult.CONTINUE
          }
        }
      })
    }
  }
}


