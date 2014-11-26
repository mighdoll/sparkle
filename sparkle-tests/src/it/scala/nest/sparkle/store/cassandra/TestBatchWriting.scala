package nest.sparkle.store.cassandra

import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}

import spray.util._

import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.serializers._

import nest.sparkle.util.ConfigUtil.sparkleConfigName

class TestBatchWriting extends FunSuite with Matchers with PropertyChecks with CassandraTestConfig
{
  import scala.concurrent.ExecutionContext.Implicits.global
  
  override def testKeySpace = "testbatchwriting"


  private def replicationFactor = 1 // ensure replication factor so we can validate

  override def configOverrides: List[(String, Any)] =
    super.configOverrides :+
      (s"$sparkleConfigName.sparkle-store-cassandra.replication-factor" -> replicationFactor)

  test("can add columnPath data") {
    withTestDb { store =>
      val columnPath = "batch/test"
      val events = Seq( 
        Event[Long,Double](1,1.1),
        Event[Long,Double](2,2.2),
        Event[Long,Double](3,3.3)
      )
      store.add(columnPath, events).await
      
      val map = store.tableBuffers
      // map.size shouldBe 5  // currently we support 5 tables
      map should contain key "bigint0double"
      val tableData = map("bigint0double")
      tableData.size shouldBe 1
      
      val data = tableData.getFirst
      data.columnPath shouldBe columnPath
      data.events should have length events.length
      data.events.head shouldBe events.head
    }
  }
}
