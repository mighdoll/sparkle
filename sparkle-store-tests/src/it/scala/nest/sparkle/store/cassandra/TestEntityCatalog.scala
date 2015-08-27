package nest.sparkle.store.cassandra

import scala.concurrent.ExecutionContext
import scala.util.{Success, Try}

import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.store.{EntityNotFoundForLookupKey, Entity, BasicColumnPathFormat}
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.RandomUtil.randomAlphaNum
import nest.sparkle.util.FutureAwait.Implicits._

class TestEntityCatalog extends FunSuite with Matchers with PropertyChecks with CassandraStoreTestConfig {

  override def testKeySpace = "testentitycatalog"

  private def replicationFactor = 1 // ensure replication factor so we can validate

  override def configOverrides: Seq[(String, Any)] =
    super.configOverrides ++
      List(s"$sparkleConfigName.sparkle-store-cassandra.replication-factor" -> replicationFactor,
        s"$sparkleConfigName.column-path-format" -> classOf[DummyEntityColumnPathFormat].getCanonicalName)

  implicit val executionContext = ExecutionContext.global

  test("catalog works") {
    withTestDb { store =>
      var testColumnPath1: Option[String] = None

      withTestColumn[Long, Double](store) { (writeColumn, testColumnPath) =>
        testColumnPath1 = Some(testColumnPath)

        val entities1 = store.entities("dummyKey1").await
        entities1.size shouldBe 1
        entities1.contains(testColumnPath) shouldBe true

        val entities2 = store.entities("dummyKey2").await
        entities2.size shouldBe 1
        entities2.contains(testColumnPath) shouldBe true
      }

      withTestColumn[Long, Double](store) { (writeColumn, testColumnPath2) =>
        val entities1 = store.entities("dummyKey1").await
        entities1.size shouldBe 2
        entities1.contains(testColumnPath2) shouldBe true
        entities1.contains(testColumnPath1.get) shouldBe true

        val entities2 = store.entities("dummyKey2").await
        entities2.size shouldBe 2
        entities2.contains(testColumnPath2) shouldBe true
        entities2.contains(testColumnPath1.get) shouldBe true
      }
    }
  }

  test("entity not found") {
    withTestDb { store =>
      withTestColumn[Long, Double](store) { (writeColumn, testColumnPath) =>
        val result = store.entities("badKey").failed.await
        result shouldBe EntityNotFoundForLookupKey("badKey")
      }
    }
  }
}

class DummyEntityColumnPathFormat extends BasicColumnPathFormat {
  override def entity(columnPath: String) : Try[Entity] = {
    Success(Entity(columnPath, Set("dummyKey1", "dummyKey2")))
  }
}


