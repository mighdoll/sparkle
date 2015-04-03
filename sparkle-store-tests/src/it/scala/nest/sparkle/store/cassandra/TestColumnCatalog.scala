package nest.sparkle.store.cassandra

import scala.reflect.runtime.universe._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.store.{ColumnCategoryNotDeterminable, ColumnCategoryNotFound, ColumnNotFound, BasicColumnPathFormat}
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
        result.getCause shouldBe ColumnCategoryNotFound(store.columnPathFormat.columnCategory(testColumnPath).get)
      }
    }
  }

  test("missing column returns error") {
    val notColumnPath = "server1/notAColumn"
    withTestDb { store =>
      val result = store.columnCatalog.catalogInfo(notColumnPath).failed.await
      result shouldBe ColumnNotFound(notColumnPath)
      result.getCause shouldBe ColumnCategoryNotFound(store.columnPathFormat.columnCategory(notColumnPath).get)
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

class DummyColumnPathFormat extends BasicColumnPathFormat {
  override def columnCategory(columnPath: String) = {
    if (columnPath.contains("server1")) Success(s"dummy/$columnPath")
    else Failure(ColumnCategoryNotDeterminable(columnPath))
  }
}
