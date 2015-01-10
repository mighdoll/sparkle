package nest.sparkle.store.cassandra

import scala.concurrent.Future
import scala.concurrent.duration._

import org.scalatest.{FunSuite, Matchers}

import spray.util._

import rx.lang.scala.Observable

import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.ObservableFuture._

class TestCassandraStreaming extends FunSuite with Matchers with CassandraStoreTestConfig {
  override def testKeySpace = "testcassandrastreaming"

  test("verify notification of ongoing writes") {
    val columnPath = "streaming/column"
    withTestActors { system =>
      import system.dispatcher
      
      withTestDb { store =>
        val done: Future[Unit] =
          for {
            writeColumn <- store.writeableColumn[Long, Long](columnPath)
            readColumn <- store.column[Long, Long](columnPath)
            read = readColumn.readRange()
            initial <- read.initial.toFutureSeq // wait for initial read to complete, all writes should appear in ongoing
          } yield {
            initial.isEmpty shouldBe true

            // write items every 100ms so that we're not writing everything thread synchronously and might not test notification
            Observable.interval(100.milliseconds).take(3).subscribe { value =>
              val event = Event(System.currentTimeMillis(), value)
              writeColumn.write(Seq(event))
            }

            val collected = read.ongoing.take(3).toBlocking.toList.map(_.value)
            collected shouldBe List(0, 1, 2)
          }

        done.await
      }
    }
  }
}