/* Copyright 2013  Nest Labs

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

import scala.util.Failure
import scala.concurrent.{ExecutionContext,Future}
import scala.concurrent.duration._
import scala.collection.JavaConverters._

import com.datastax.driver.core.Session

import spray.util._

import org.scalatest.{Matchers,FunSuite,BeforeAndAfterEach}
import org.scalatest.prop.PropertyChecks

import org.scalacheck.Gen

import com.typesafe.config.ConfigFactory

import nest.sparkle.store.{Store, Event}
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.ConfigUtil
import nest.sparkle.util.GuavaConverters._

class TestCassandraStore extends FunSuite
                                 with Matchers
                                 with PropertyChecks
                                 with BeforeAndAfterEach
{
  import ExecutionContext.Implicits.global

  val config = {
    val source = ConfigFactory.load().getConfig("sparkle-time-server")
    ConfigUtil.modifiedConfig(
      source, 
      Some("sparkle-store-cassandra.key-space","testcassandrastore")
    )
  }
  val storeConfig = config.getConfig("sparkle-store-cassandra")
  val testContactHosts = storeConfig.getStringList("contact-hosts").asScala.toSeq
  val testKeySpace = storeConfig.getString("key-space")
  val testColumn = "latency.p99"
  val testId = "server1"
  val columnPath = s"$testId/$testColumn"

  var testDb: Option[CassandraStore] = None 
  
  override def beforeEach() = {
    CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
    val store = CassandraStore(config)
    val column = store.writeableColumn[NanoTime, Double](columnPath).await
    column.create("a test column").await
    
    testDb = Some(store)
    
    super.beforeEach()
  }

  override def afterEach() = {
    try {
      super.afterEach()
    } finally {
      testDb.foreach(_.close().toFuture.await(10.seconds))
      testDb = None
      CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
    }
  }

  /** recreate the database and a test column */
  def withTestDb[T](fn: CassandraStore => T): T = {
    testDb.map(fn).get
  }

  /**
   * Sanity test to validate store can be created w/o an exception.
   */
  test("create event schema and catalog") {
    withTestDb { _ => }
  }
  
  test("erase works") {
    testDb.map{ store => 
      store.format()
      // columnPath no longer in the store
      val result = store.columnCatalog.tableForColumn(columnPath).failed.await
      result shouldBe ColumnNotFound(columnPath)
    }
  }

  test("missing column returns error") {
    val notColumn = "notAColumn"
    withTestDb { store =>
      val table = store.columnCatalog.tableForColumn(notColumn)
      val result = table.failed.await
      result shouldBe ColumnNotFound(notColumn)
    }
  }
  test("missing column !exists") {
    val notColumn = "foo/notAColumn"
    withTestDb { store =>
      val column = store.column[Int, Double](notColumn).await
      val result = column.exists.failed.await
      result shouldBe ColumnNotFound(notColumn)
    }
  }

  test("read+write one item") {
    withTestDb { store =>
      val writeColumn = store.writeableColumn[Long, Double](columnPath).await
      writeColumn.write(Seq(Event(100L, 1.1d))).await
      
      val readColumn = store.column[Long, Double](columnPath).await
      val read = readColumn.readRange(None, None)
      val results = read.toBlockingObservable.single
      results shouldBe Event(100L, 1.1d)
    }
  }

  test("read+write many events") {
    withTestDb { implicit store =>
      val writeColumn = store.writeableColumn[Long, Double](columnPath).await
      val readColumn = store.column[Long, Double](columnPath).await
      forAll(Gen.chooseNum(0, 1000), minSuccessful(10)) { rowCount =>
        writeColumn.erase().await
        
        val events = Range(0, rowCount).map { index =>
          Event(index.toLong, index.toDouble)
        }.toIterable
        
        writeColumn.write(events).await
        val read = readColumn.readRange(None, None)
        val results = read.toBlockingObservable.toList
        results.length shouldBe rowCount
        results.zipWithIndex.foreach {
          case (item, index) =>
            item shouldBe Event(index, index)
        }
      }
    }
  }

  test("multi-level dataset column") {
    withTestDb { store =>
      val parts = Array("a", "b", "c", "d", "e", "column")
      val paths = parts.scanLeft("") {
        case ("",x) => x
        case (last,x) => last + "/" + x
      }.tail
      val writeColumn = store.writeableColumn[Long, Double](paths.last).await
      writeColumn.create("a column with multi-level datasets").await
      
      // Each part except for the last should have a single dataset child
      paths.dropRight(1).sliding(2).foreach { case Array(parent,child) =>
        val root = store.dataSet(parent).await
        val childDataSets = root.childDataSets.toBlockingObservable.toList
        childDataSets.length shouldBe 1
        childDataSets(0).name shouldBe child
      }
      
      // The last dataset path should have one child that is a column
      paths.takeRight(2).sliding(2).foreach { case Array(parent,child) =>
        val root = store.dataSet(parent).await
        val columns = root.childColumns.toBlockingObservable.toList
        columns.length shouldBe 1
        columns(0) shouldBe child
      }
    }
  }

  test("multi-level dataset with 2 columns") {
    withTestDb { store =>
      val parts1 = Array("a", "b", "c", "d", "e", "columnZ")
      val paths1 = parts1.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn1 = store.writeableColumn[Long, Double](paths1.last).await
      writeColumn1.create("a column with multi-level datasets").await
    
      val parts2 = Array("a", "b", "c", "d", "e", "columnA")
      val paths2 = parts2.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn2 = store.writeableColumn[Long, Double](paths2.last).await
      writeColumn2.create("a column with multi-level datasets").await
      
      // Each part except for the last should have a single dataset child
      paths1.dropRight(1).sliding(2).foreach { case Array(parent,child) =>
        val root = store.dataSet(parent).await
        val childDataSets = root.childDataSets.toBlockingObservable.toList
        childDataSets.length shouldBe 1
        childDataSets(0).name shouldBe child
      }
      
      // The last dataset path should have two children that are columns
      paths1.takeRight(2).dropRight(1).foreach { parent =>
        val root = store.dataSet(parent).await
        val columns = root.childColumns.toBlockingObservable.toList
        columns.length shouldBe 2
        // Note that writeColumn1 should sort *after* writeColumn2.
        columns(0) shouldBe paths2.last
        columns(1) shouldBe paths1.last
      }
    }
  }

  test("multi-level dataset with 2 datasets at the second level") {
    withTestDb { store =>
      val parts1 = Array("a", "b", "c1", "column")
      val paths1 = parts1.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn1 = store.writeableColumn[Long, Double](paths1.last).await
      writeColumn1.create("a column with multi-level datasets").await
    
      val parts2 = Array("a", "b", "c2", "column")
      val paths2 = parts2.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn2 = store.writeableColumn[Long, Double](paths2.last).await
      writeColumn2.create("a column with multi-level datasets").await
      
      // "a/b" should have two children, "a/b/c1" and "a/b/c2"
      val root = store.dataSet("a/b").await
      val childDataSets = root.childDataSets.toBlockingObservable.toList
      childDataSets.length shouldBe 2
      childDataSets(0).name shouldBe "a/b/c1"
      childDataSets(1).name shouldBe "a/b/c2"
      
      // Ensure c level datasets have one child column
      Array("a/b/c1", "a/b/c2").foreach { parent =>
        val root = store.dataSet(parent).await
        val columns = root.childColumns.toBlockingObservable.toList
        columns.length shouldBe 1
        columns(0) shouldBe (parent+"/"+parts1.last)
      }
    }
  }

}
