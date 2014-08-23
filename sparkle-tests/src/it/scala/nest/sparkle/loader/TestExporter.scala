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
import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.{ Future, Promise }
import org.scalatest.{ FunSuite, Matchers }
import org.slf4j.LoggerFactory
import akka.actor._
import akka.util.Timeout.longToTimeout
import spray.util._
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.tools.Exporter
import nest.sparkle.util.Resources
import nest.sparkle.tools.FileExporter
import scala.concurrent.duration._
import scala.collection.JavaConverters._

class TestExporter extends FunSuite with CassandraTestConfig with Matchers {
  override def testKeySpace = "testexporter"
  val exportDirectory = "/tmp/testexporter"
  
  override def configOverrides = super.configOverrides ++
      List("exporter.output" -> exportDirectory)

  test("export epochs.csv to exports.tsv and validate the exported contents") {
    withLoadedFile("epochs.csv") { (store, system) =>
      
      import system.dispatcher

      withDeleteDirectory(exportDirectory) {
        val exporter = FileExporter(rootConfig, store)
        exporter.exportFiles("epochs").await(10.seconds)
        
        val exportedFilePath = Paths.get(exportDirectory).resolve("epochs.tsv")
        val lines = Files.readAllLines(exportedFilePath, UTF_8).asScala
        lines.size shouldBe 2752
        lines.head shouldBe "key\tcount\tp90\tp99"
        lines(1) shouldBe "1357710556000\t1402\t0.000604\t0.00139"
      }
    }
  }
  
  /** recursively delete a provided directory after running a provided function (delet even if the function throws) */
  private def withDeleteDirectory[T](pathString: String)(fn: => T): T = {
    try {
      fn
    } finally {
      cleanDirectory(Paths.get(pathString))
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


