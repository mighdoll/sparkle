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


  private def replicationFactor = 1 // only 1 server in test

  override def configOverrides: List[(String, Any)] =
    super.configOverrides :+
      (s"$sparkleConfigName.sparkle-store-cassandra.replication-factor" -> replicationFactor)
  
  val columnPath = "batch/test"
  val events = Vector( 
    Event[Long,Double](1,1.1),
    Event[Long,Double](2,2.2),
    Event[Long,Double](3,3.3)
  )
  
  def checkStoreQueue(store: CassandraReaderWriter): Unit = {
    val map = store.tableQueues
    // map.size shouldBe 5  // currently we support 5 tables
    map should contain key "bigint0double"
    val tableQueue = map("bigint0double")
    tableQueue should have length 3
    
    val data = tableQueue.dequeue()
    data.columnPath shouldBe columnPath
    data.key shouldBe events(0).argument
    data.value shouldBe events(0).value
  }
  
  test("can enqueue columnPath data") {
    withTestDb { store =>
      store.enqueue(columnPath, events)
      
      checkStoreQueue(store)
    }
  }

  test("can enqueue columnPath data acquiring lock") {
    withTestDb { store =>
      
      store.acquireEnqueueLock()
      store.enqueue(columnPath, events)
      
      checkStoreQueue(store)
      
      store.releaseEnqueueLock()
    }
  }

  test("queue is cleared after flush") {
    withTestDb { store =>
      
      store.acquireEnqueueLock()
      store.enqueue(columnPath, events)
      store.releaseEnqueueLock()
      
      store.flush()
      
      val tableQueue = store.tableQueues("bigint0double")
      tableQueue should have length 0
    }
  }
}
