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

import java.nio.ByteBuffer

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
import nest.sparkle.util.{ConfigUtil, Resources, Log, GenericFlags}
import nest.sparkle.store.cassandra.CassandraStore
import nest.sparkle.util.GuavaConverters._
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.store.Event
import spray.json.JsValue

class TestFilesLoader extends FunSuite with Matchers with CassandraStoreTestConfig {
  override def testKeySpace = "testfilesloader"

  override def testConfigFile: Option[String] = Some("tests")

  /** try loading a known file and check the expected column for results */
  def testEpochsFile(resourcePath: String, columnPath: String) {
    testLoadFile(resourcePath, columnPath) { results: Seq[Event[Long, Double]] =>
      results.length shouldBe 2751
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

  test("load csv file in dir/subdir") {
    testEpochsFile("dir", "subdir/epochs/count")
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
      results.tail.head shouldBe Event(2L, 2L)
    }
  }

  test("load file with explicit boolean") {
    testLoadFile("explicitTypes.csv", "explicitTypes/boo") { results: Seq[Event[Long, Boolean]] =>
      results.head.value shouldBe true
    }
  }

  ignore("load file with explicit short") {
    testLoadFile("explicitTypes.csv", "explicitTypes/sho") { results: Seq[Event[Long, Short]] =>
      results.head.value shouldBe 1
    }
  }

  test("load file with explicit int") {
    testLoadFile("explicitTypes.csv", "explicitTypes/int") { results: Seq[Event[Long, Int]] =>
      results.head.value shouldBe 2
    }
  }

  test("load file with explicit long") {
    testLoadFile("explicitTypes.csv", "explicitTypes/lon") { results: Seq[Event[Long, Long]] =>
      results.head.value shouldBe 3
    }
  }

  ignore("load file with explicit char") {
    testLoadFile("explicitTypes.csv", "explicitTypes/cha") { results: Seq[Event[Long, Char]] =>
      results.head.value shouldBe 'c'
    }
  }

  test("load file with explicit string") {
    testLoadFile("explicitTypes.csv", "explicitTypes/str") { results: Seq[Event[Long, Char]] =>
      results.head.value shouldBe "st"
    }
  }

  test("load file with explicit json") {
    import spray.json._
    testLoadFile("explicitTypes.csv", "explicitTypes/jso") { results: Seq[Event[Long, JsValue]] =>
      val expected = """{ "js": 9 }""".asJson
      results.head.value shouldBe expected
    }
  }

  test("load file with explicit generic flags") {
    testLoadFile("explicitTypes.csv", "explicitTypes/fla") { results: Seq[Event[Long, GenericFlags]] =>
      val expected = GenericFlags(123)
      results.head.value shouldBe expected
    }
  }

  test("load file with explicit blob") {
    import com.google.common.base.Charsets
    testLoadFile("explicitTypes.csv", "explicitTypes/blo") { results: Seq[Event[Long, ByteBuffer]] =>
      val expected = ByteBuffer.wrap("abc".getBytes(Charsets.UTF_8))
      results.head.value shouldBe expected
    }
  }

}

