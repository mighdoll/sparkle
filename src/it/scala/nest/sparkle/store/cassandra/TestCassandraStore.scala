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

import org.scalatest.Matchers
import org.scalatest.FunSuite
import nest.sparkle.store.cassandra.serializers._
import com.datastax.driver.core.Session
import scala.concurrent.ExecutionContext
import spray.util._
import scala.util.Failure
import org.scalatest.prop.PropertyChecks
import org.scalacheck.Gen
import scala.concurrent.Future
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration._
import nest.sparkle.graph.Event

class TestCassandraStore extends FunSuite with Matchers with PropertyChecks with BeforeAndAfterAll {
  import ExecutionContext.Implicits.global

  val testColumn = "latency.p99"
  val testId = "server1"
  val columnPath = s"$testId/$testColumn"

  lazy val testDb = {
    val store = CassandraStore("localhost")
    store.formatLocalDb("testCassandraStoreEvents")
    val column = store.writeableColumn[NanoTime, Double](columnPath).await
    column.create("a test column").await
    store
  }

  override def afterAll() {
    testDb.close()
  }

  /** recreate the database and a test column */
  def withTestDb[T](fn: CassandraStore => T): T = {
    fn(testDb)
  }

  test("create event schema and catalog") {
    withTestDb { _ => }
  }

  test("missing column returns error") {
    val notColumn = "notAColumn"
    withTestDb { store =>
      val table = store.catalog.tableForColumn(notColumn)
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
      val writeColumn = store.writeableColumn[NanoTime, Double](columnPath).await
      writeColumn.write(Seq(Event(NanoTime(100), 1.1d))).await
      
      val readColumn = store.column[NanoTime, Double](columnPath).await
      val read = readColumn.readRange(None, None)
      val results = read.toBlockingObservable.single
      results shouldBe Event(NanoTime(100), 1.1d)
    }
  }

  test("read+write many events") {
    withTestDb { implicit store =>
      val writeColumn = store.writeableColumn[NanoTime, Double](columnPath).await
      val readColumn = store.column[NanoTime, Double](columnPath).await
      forAll(Gen.chooseNum(0, 1000), minSuccessful(10)) { rowCount =>
        writeColumn.erase().await
        
        val events = Range(0, rowCount).map { index =>
          Event(NanoTime(index), index.toDouble)
        }.toIterable
        
        writeColumn.write(events).await
        val read = readColumn.readRange(None, None)
        val results = read.toBlockingObservable.toList
        results.length shouldBe rowCount
        results.zipWithIndex.foreach {
          case (item, index) =>
            item shouldBe Event(NanoTime(index), index.toDouble)
        }
      }
    }
  }

}
