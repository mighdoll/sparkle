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

import java.util.concurrent.TimeUnit
import java.io.IOException
import java.nio.file.{Path, Paths, Files, FileVisitResult, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.charset.StandardCharsets

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.collection.JavaConverters._

import org.slf4j.LoggerFactory

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem

import spray.util._

import nest.sparkle.util.ConfigUtil
import nest.sparkle.store.cassandra.CassandraStore
import nest.sparkle.util.GuavaConverters._
import nest.sparkle.tools.Exporter

class TestExporter extends FunSuite with Matchers with BeforeAndAfterAll {
  val log = LoggerFactory.getLogger(classOf[TestExporter])
  
  val config = {
    ConfigUtil.modifiedConfig(
      ConfigFactory.load(), 
      "exporter.timeout" -> "10s", "exporter.output" -> "/tmp/testexporter",
      "sparkle-time-server.sparkle-store-cassandra.key-space" -> "testexporter"
    )
  }
  val sparkleConfig =  config.getConfig("sparkle-time-server")
  val storeConfig = sparkleConfig.getConfig("sparkle-store-cassandra")
  val contactHosts = storeConfig.getStringList("contact-hosts").asScala.toSeq
  val keySpace = storeConfig.getString("key-space")
  lazy val testDb = CassandraStore(sparkleConfig)
  
  implicit val system = ActorSystem("test-exporter")
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
    val promise = Promise[Unit]()
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[LoadComplete])

    promise.future
  }

  test("export tsv file") {
    // First load some data
    val filePath = "sparkle/src/test/resources/epochs.csv"
    FilesLoader(filePath, testDb)
    onLoadComplete(filePath).await
    
    val output = Paths.get(config.getString("exporter.output"))
    output match {
      case p if Files.isDirectory(p) => cleanDirectory(p)
      case f if Files.exists(f)      => throw new RuntimeException(s"${output.toString} is a file")
      case _                         => Files.createDirectory(output)
    }
    
    val timeout = config.getDuration("exporter.timeout", TimeUnit.MILLISECONDS)
    Exporter(config).processDataSet(filePath).await(timeout)
    
    val dataset = output.resolve(filePath)
    Files.exists(dataset.resolve("_count.tsv")) should be (true)
    Files.exists(dataset.resolve("_p90.tsv")) should be (true)
    Files.exists(dataset.resolve("_p99.tsv")) should be (true)
    
    val lines = Files.readAllLines(dataset.resolve("_count.tsv"), StandardCharsets.UTF_8)
    lines.size shouldBe 2752
    lines.get(0) shouldBe "time\tcount"
    lines.get(2751) shouldBe "1357713357000\t570.0"
  }

  /**
    * Remove all the files in a directory recursively.
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
            case _:IOException => throw e
            case _             => {
              Files.delete(dir)
              FileVisitResult.CONTINUE
            }
          }
        }
      })
    }
  }
}


