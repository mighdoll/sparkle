package nest.sparkle.shell

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.loader.spark.SparkTestConfig
import nest.sparkle.store.cassandra.CassandraStoreReader
import nest.sparkle.util.ConfigUtil

class TestSparkConnection extends FunSuite with Matchers with CassandraStoreTestConfig
    with SparkTestConfig {

  override def testKeySpace = "testsparkconnection"

  def withSpark(fn: SparkConnection => Unit): Unit = {
    val modifiedConfig = ConfigUtil.modifiedConfig(rootConfig,
      "sparkle.sparkle-store-cassandra.key-space" -> testKeySpace)
    val connection = SparkConnection(modifiedConfig, "TestSparkConnection")
    try {
      fn(connection)
    } finally {
      connection.close()
      connection.sparkContext.stop()
    }
  }

  def withSparkTest(file: String)(fn: (SparkConnection, CassandraStoreReader) => Unit): Unit = {
    withLoadedFile(file) { (store, system) =>
      withSpark { connection =>
        fn(connection, store)
      }

    }
  }

  def countElementsByType(file: String)(fn: (Map[String,Long]) => Unit): Unit = {
    withSparkTest(file) { (connection, store) =>
      val grouped =
        connection.allData.groupBy(_.valueTypeTag)
      val result =
        grouped.map { case (valueType, columns) =>
          val columnElementCount = columns.map(_.data.length.toLong).sum
          (valueType.toString, columnElementCount)
        }
      val byType = result.collect.toMap
      fn(byType)
    }
  }

  test("count total elements in the system") {
    val connection = SparkConnection(rootConfig, "TestSparkConnection")

    withSparkTest("epochs.csv") { (connection, store) =>
      val sum = connection.allData.map(_.data.length).collect.sum
      sum shouldBe 8253
    }
  }

  test("count elements by type") {
    countElementsByType("epochs.csv") { (byType) =>
      byType("Double") shouldBe 5502
      byType("Long") shouldBe 2751
    }
  }

  test("count elements by type (various types)") {
    countElementsByType("types.csv") { (byType) =>
      byType("Int") shouldBe 3
      byType("Long") shouldBe 6
      byType("Double") shouldBe 9
      byType("String") shouldBe 12
      byType("Boolean") shouldBe 15
    }
  }

  test("count elements by type (explicit types)") {
    countElementsByType("explicitTypes.csv") { (byType) =>
      byType("Int") shouldBe 1
      byType("Long") shouldBe 2 // short currently treated as long, see TypedColumnHeader
      byType("Double") shouldBe 1
      byType("String") shouldBe 3
      byType("Boolean") shouldBe 1
    }
  }
}