package nest.sparkle.shell

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.store.cassandra.ColumnTypes
import scala.reflect.runtime.universe._
import org.apache.spark.rdd.RDD
import nest.sparkle.store.Event
import nest.sparkle.loader.spark.SparkTestConfig
import nest.sparkle.store.cassandra.CassandraStoreReader
import nest.sparkle.util.FutureAwait.Implicits._

class TestSparkConnection extends FunSuite with Matchers with CassandraStoreTestConfig
    with SparkTestConfig {

  override def testKeySpace = "testsparkconnection"

  def withSpark(fn: SparkConnection => Unit): Unit = {
    val connection = SparkConnection(rootConfig, "TestSparkConnection")
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

  test("count total elements in the system") {
    val connection = SparkConnection(rootConfig, "TestSparkConnection")

    withSparkTest("epochs.csv") { (connection, store) =>
      val sum = connection.allData.map(_.data.length).collect.sum
      sum shouldBe 8253
    }
  }

  test("count elements by type") {
    withSparkTest("epochs.csv") { (connection, store) =>
      val grouped = 
        connection.allData.groupBy(_.valueTypeTag)
      val result = 
        grouped.map { case (valueType, columns) =>
          val columnElementCount = columns.map(_.data.length.toLong).sum
          (valueType.toString, columnElementCount)
        }
      val byType = result.collect.toMap
      byType("Double") shouldBe 5502
      byType("Long") shouldBe 2751
    }
  }

}