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

import nest.sparkle.store.{MalformedColumnPath, ColumnNotFound}
import spray.util._

import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.RandomUtil.randomAlphaNum
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, FunSuite}

import scala.reflect.runtime.universe._

class TestColumnCatalog extends FunSuite with Matchers with PropertyChecks with CassandraTestConfig {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def testKeySpace = "testcolumncatalog"

  private def replicationFactor = 1 // ensure replication factor so we can validate

  private def catalogTableCustomizer = new TestCatalogTableCustomizer

  override def configOverrides: List[(String, Any)] =
    super.configOverrides ++ List(
      s"$sparkleConfigName.sparkle-store-cassandra.replication-factor" -> replicationFactor,
      s"$sparkleConfigName.sparkle-store-cassandra.catalog-table-customizer" -> classOf[TestCatalogTableCustomizer].getCanonicalName
    )

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
      }
    }
  }

  test("erase works") {
    withTestDb { store =>
      withTestColumn[Long, Double](store) { (writeColumn, testColumnPath) =>
        store.format()
        // custom columnPath no longer in the store
        val customColumnPath = catalogTableCustomizer.customizeColumnPath(testColumnPath).get
        val result = store.columnCatalog.catalogInfo(testColumnPath).failed.await
        result shouldBe ColumnNotFound(customColumnPath)
      }
    }
  }

  test("missing column returns error") {
    val notColumnPath = "server1/notAColumn"
    withTestDb { store =>
      val customColumnPath = catalogTableCustomizer.customizeColumnPath(notColumnPath).get
      val result = store.columnCatalog.catalogInfo(notColumnPath).failed.await
      result shouldBe ColumnNotFound(customColumnPath)
    }
  }

  test("malformed column returns error") {
    val notColumnPath = "notAColumn"
    withTestDb { store =>
      val result = store.columnCatalog.catalogInfo(notColumnPath).failed.await
      result shouldBe MalformedColumnPath(notColumnPath)
    }
  }
}

class TestCatalogTableCustomizer extends CatalogTableCustomizer {
  def customizeColumnPath(columnPath: String): Option[String] = {
    if (columnPath.contains("server1")) Some(s"test/$columnPath")
    else None
  }
}
