package nest.sparkle.store.cassandra

import scala.util.Random

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
  
  val testColumnPath = "batch/test"
  val testEvents = Vector( 
    Event[Long,Double](1,1.1),
    Event[Long,Double](2,2.2),
    Event[Long,Double](3,3.3)
  )
  
  def writeEvents(store: CassandraReaderWriter, columnPath: String, events: Iterable[Event[Long,Double]]): Unit = {
      store.acquireEnqueueLock()
      store.enqueue(columnPath, events)
      store.releaseEnqueueLock()
      
      store.flush()
  }
  
  def checkStoreQueue(store: CassandraReaderWriter): Unit = {
    val map = store.tableQueues
    // map.size shouldBe 5  // currently we support 5 tables
    map should contain key "bigint0double"
    val tableQueue = map("bigint0double")
    tableQueue should have length 3
    
    val data = tableQueue.dequeue()
    data.columnPath shouldBe testColumnPath
    data.key shouldBe testEvents(0).argument
    data.value shouldBe testEvents(0).value
  }
  
  test("can enqueue columnPath data") {
    withTestDb { store =>
      store.enqueue(testColumnPath, testEvents)
      
      checkStoreQueue(store)
    }
  }

  test("can enqueue columnPath data acquiring lock") {
    withTestDb { store =>
      
      store.acquireEnqueueLock()
      store.enqueue(testColumnPath, testEvents)
      
      checkStoreQueue(store)
      
      store.releaseEnqueueLock()
    }
  }

  test("queue is cleared after flush") {
    withTestDb { store =>
      writeEvents(store, testColumnPath, testEvents)
      
      val tableQueue = store.tableQueues("bigint0double")
      tableQueue should have length 0
    }
  }

  test("expected rows are written") {
    withTestDb { store =>
      writeEvents(store, testColumnPath, testEvents)
      
      val column = store.column[Long,Double](testColumnPath).await
      val readEvents = column.readRange().initial.toBlocking.toList
      readEvents should have length testEvents.length
    }
  }
  
  /*
   * This test start W worker threads which write events to bigint0double.
   * There are waits at the start and between enqueuing events to simulate writers running at
   * different rates. The later threads wait longer meaning they are "slower" to process.
   */
  test("multiple threads can write") {
    withTestDb { store =>
      val numWriters = 9
      val numEvents = 100
      def wsleep(n: Int = 1000) = Thread.sleep(n.toLong)
      val writers: Seq[Thread] = (1 to numWriters).map { id =>
        val thread = new Thread {
          val scale = id * 1000
          val events = (0 until numEvents)
            .map { ii => Event[Long, Double](ii+scale, ii+scale.toDouble)}
          
          override def run(): Unit = {
            wsleep(id * 500)
            
            store.acquireEnqueueLock()
            store.enqueue(testColumnPath, events.take(numEvents/2))
            store.releaseEnqueueLock()
            
            wsleep(id * 500)
            
            store.acquireEnqueueLock()
            store.enqueue(testColumnPath, events.takeRight(numEvents/2))
            store.releaseEnqueueLock()
            
            store.flush()
          }
        }
        thread.start()
        thread
      }
      
      val timeout = 5 * 30 * 1000L
      writers.foreach(_.join(timeout))  // Wait for all the writers to end.
      
      val tableQueue = store.tableQueues("bigint0double")
      tableQueue should have length 0
      
      val column = store.column[Long,Double](testColumnPath).await
      val readEvents = column.readRange().initial.toBlocking.toList
      readEvents should have length (numWriters * numEvents)
    }
  }
}
