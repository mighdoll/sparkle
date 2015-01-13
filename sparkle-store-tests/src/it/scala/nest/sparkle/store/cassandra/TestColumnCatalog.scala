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

package nest.sparkle.store.cassandra

import scala.reflect.runtime.universe._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.store.{ColumnCategoryNotDeterminable, ColumnCategoryNotFound, ColumnNotFound, ColumnPathFormat}
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.RandomUtil.randomAlphaNum
import nest.sparkle.util.FutureAwait.Implicits._

class TestColumnCatalog extends FunSuite with Matchers with PropertyChecks with CassandraStoreTestConfig {

  override def testKeySpace = "testcolumncatalog"

  private def replicationFactor = 1 // ensure replication factor so we can validate

  override def configOverrides: List[(String, Any)] =
    super.configOverrides :+
      (s"$sparkleConfigName.sparkle-store-cassandra.replication-factor" -> replicationFactor)
  
  implicit val executionContext = ExecutionContext.global

  def withTestColumn[T: CanSerialize, U: CanSerialize](store: CassandraStoreWriter) // format: OFF
      (fn: (WriteableColumn[T,U], String) => Unit): Unit = { // format: ON
    val testColumn = s"latency.p99.${randomAlphaNum(3)}"
    val testId = "server1"
    val testColumnPath = s"$testId/$testColumn"
    val column = store.writeableColumn[T, U](testColumnPath).await
    try {
      fn(column, testColumnPath)
    }
  }

  test("catalog works") {
    withTestDb { store =>
      withTestColumn[Long, Double](store) { (writeColumn, testColumnPath) =>
        val catalogInfo = store.columnCatalog.catalogInfo(testColumnPath).await
        catalogInfo.tableName shouldBe "bigint0double"
        catalogInfo.keyType shouldBe typeTag[Long]
        catalogInfo.valueType shouldBe typeTag[Double]
        // now erase
        store.format()
        val result = store.columnCatalog.catalogInfo(testColumnPath).failed.await
        result shouldBe ColumnNotFound(testColumnPath)
        result.getCause shouldBe ColumnCategoryNotFound(store.columnCatalog.columnPathFormat.getColumnCategory(testColumnPath).get)
      }
    }
  }

  test("missing column returns error") {
    val notColumnPath = "server1/notAColumn"
    withTestDb { store =>
      val result = store.columnCatalog.catalogInfo(notColumnPath).failed.await
      result shouldBe ColumnNotFound(notColumnPath)
      result.getCause shouldBe ColumnCategoryNotFound(store.columnCatalog.columnPathFormat.getColumnCategory(notColumnPath).get)
    }
  }
}

class TestColumnCatalogWithNonDefaultColumnPathFormat extends TestColumnCatalog {

  override def testKeySpace = "testcolumncatalognondefault"

  override def configOverrides: List[(String, Any)] =
    super.configOverrides :+
      (s"$sparkleConfigName.column-path-format" -> classOf[DummyColumnPathFormat].getCanonicalName)

  test("column category cannot be determined for a column path") {
    val notColumnPath = "notAColumn"
    withTestDb { store =>
      val result = store.columnCatalog.catalogInfo(notColumnPath).failed.await
      result shouldBe ColumnNotFound(notColumnPath)
      result.getCause shouldBe ColumnCategoryNotDeterminable(notColumnPath)
    }
  }
}

class DummyColumnPathFormat extends ColumnPathFormat {
  def getColumnCategory(columnPath: String): Try[String] = {
    if (columnPath.contains("server1")) Success(s"dummy/$columnPath")
    else Failure(ColumnCategoryNotDeterminable(columnPath))
  }
}
