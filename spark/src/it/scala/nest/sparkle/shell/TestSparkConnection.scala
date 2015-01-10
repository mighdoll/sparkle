package nest.sparkle.shell

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.store.cassandra.ColumnTypes
import scala.reflect.runtime.universe._
import org.apache.spark.rdd.RDD
import nest.sparkle.store.Event
import nest.sparkle.loader.spark.SparkTestConfig

class TestSparkConnection extends FunSuite with Matchers with CassandraStoreTestConfig
  with SparkTestConfig {

  override def testKeySpace = "testsparkconnection"

  test("count total elements in the system") {
    val connection = SparkConnection(rootConfig, "TestSparkConnection")

    withTestDb { store =>
      try {
        val valueTypes = Seq(
          typeTag[Long], typeTag[Double], typeTag[Int], typeTag[Boolean], typeTag[String])
        val rdds: Seq[RDD[Any]] = valueTypes map { valueType =>
          connection.columnsRDD(typeTag[Long], valueType).asInstanceOf[RDD[Any]]
        }

        val combined = rdds.reduce((a, b) => a ++ b)
        val sum = combined.count

        println(s"$sum total events stored in the keyspace: ")
      } finally {
        connection.close()
      }
    }
  }
}