package nest.sparkle.store.cassandra

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.store.cassandra.serializers._
import rx.lang.scala.Observable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import nest.sparkle.store.Event
import nest.sparkle.util.ObservableFuture._
import spray.util._

class TestCassandraStreaming extends FunSuite with Matchers with CassandraTestConfig {
  override def testKeySpace = "testcassandrastreaming"
  import ExecutionContext.Implicits.global

  test("verify notification") {
    val columnPath = "streaming/column"
    withTestDb { store =>
      val futureWriteColumn = store.writeableColumn[Long, Double](columnPath)
      val futureSubscribe =
        futureWriteColumn.map { writeColumn =>
          val subscribe =
            Observable.interval(100.milliseconds).subscribe { item =>
              val event = Event(System.currentTimeMillis(), item.toDouble)
              writeColumn.write(Seq(event))
            }

          val ongoing =
            store.column[Long, Double](columnPath).toObservable.flatMap { column =>
              column.readRange().ongoing
            }
          
          val collected = ongoing.take(3).toBlocking.toList.map(_.value)
          collected shouldBe List(0,1,2)

          subscribe
        }
      futureSubscribe.await.unsubscribe()
    }
  }
}