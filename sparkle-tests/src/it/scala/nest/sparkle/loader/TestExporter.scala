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

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import org.scalatest.{ FunSuite, Matchers }
import spray.util._
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.util.FileUtil
import nest.sparkle.tools.FileExporter
import scala.concurrent.duration._
import scala.collection.JavaConverters._

class TestExporter extends FunSuite with CassandraStoreTestConfig with Matchers {
  override def testKeySpace = "testexporter"
  val exportDirectory = "/tmp/testexporter"
  
  override def configOverrides = super.configOverrides ++
      List("exporter.output" -> exportDirectory)

  test("export epochs.csv to exports.tsv and validate the exported contents") {
    withLoadedFile("epochs.csv") { (store, system) =>

      import system.dispatcher

      withDeleteDirectory(exportDirectory) {
        val exporter = FileExporter(rootConfig, store)
        exporter.exportLeafDataSet("epochs").await(10.seconds)

        val exportedFilePath = Paths.get(exportDirectory).resolve("epochs.tsv")
        val lines = Files.readAllLines(exportedFilePath, UTF_8).asScala
        lines.size shouldBe 2752
        lines.head shouldBe "key\tcount\tp90\tp99"
        lines(1) shouldBe "1357710556000\t1402\t0.000604\t0.00139"
      }
    }
  }

  test("export dir/subdir/epochs.csv to subdir/epochs.csv and validate the exported contents") {
    withLoadedFile("dir") { (store, system) =>

      import system.dispatcher

      withDeleteDirectory(exportDirectory) {
        val exporter = FileExporter(rootConfig, store)
        exporter.exportLeafDataSet("subdir/epochs").await(10.seconds)

        val exportedFilePath = Paths.get(exportDirectory).resolve("subdir/epochs.tsv")
        val lines = Files.readAllLines(exportedFilePath, UTF_8).asScala
        lines.size shouldBe 2752
        lines.head shouldBe "key\tcount\tp90\tp99"
        lines(1) shouldBe "1357710556000\t1402\t0.000604\t0.00139"
      }
    }
  }

  test("export epochs/p90 of epochs.csv to epochs.tsv and validate the exported contents") {
    withLoadedFile("epochs.csv") { (store, system) =>

      import system.dispatcher

      withDeleteDirectory(exportDirectory) {
        val exporter = FileExporter(rootConfig, store)
        exporter.exportColumn("epochs/p90").await(10.seconds)

        val exportedFilePath = Paths.get(exportDirectory).resolve("epochs.tsv")
        val lines = Files.readAllLines(exportedFilePath, UTF_8).asScala
        lines.size shouldBe 2752
        lines.head shouldBe "key\tp90"
        lines(1) shouldBe "1357710556000\t0.000604"
      }
    }
  }

  /** recursively delete a provided directory after running a provided function (delete even if the function throws) */
  private def withDeleteDirectory[T](pathString: String)(fn: => T): T = {
    try {
      fn
    } finally {
      FileUtil.cleanDirectory(Paths.get(pathString))
    }
  }

}


