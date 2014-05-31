package nest.sg

import scala.concurrent.{ Future, Promise }
import org.scalatest.{ FunSuite, Matchers }
import akka.actor.ActorSystem
import nest.sparkle.loader.{ LoadComplete, ReceiveLoaded }
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.util.Resources
import nest.sparkle.loader.FilesLoader
import spray.util._

class TestStorageConsole extends FunSuite with Matchers with CassandraTestConfig {
  val filePath = Resources.filePathString("epochs.csv")

  /** return a future that completes when the loader reports that loading is complete */
  def onLoadComplete(system: ActorSystem, path: String): Future[Unit] = {
    // TODO Get rid of this copy/pasted onLoadComplete (by moving files loader to stream loader notification)
    val promise = Promise[Unit]
    system.eventStream.subscribe(system.actorOf(ReceiveLoaded.props(path, promise)),
      classOf[LoadComplete])

    promise.future
  }

  def withTestConsole[T](fn: StorageConsoleAPI => T): T = {    
    withTestDb { testDb =>
      withTestActors{ implicit system =>
        val complete = onLoadComplete(system, "epochs/count")
        FilesLoader(filePath, testDb)
        complete.await
        val storageConsole = new ConcreteStorageConsole(testDb, system.dispatcher)
        fn(storageConsole)
      }
    }
  }

  test("all columns") {
    withTestConsole { storageConsole =>
      val allColumns = storageConsole.allColumns().toBlockingObservable.toList
      allColumns.toSet shouldBe Set("epochs/count", "epochs/p99", "epochs/p90")
    }
  }

  test("eventsByDataSet") {
    withTestConsole { storageConsole =>
      val allEvents = storageConsole.eventsByDataSet("epochs")
      allEvents.length shouldBe 3
      allEvents.map(_.name).toSet shouldBe Set("epochs/count", "epochs/p99", "epochs/p90")
      allEvents.map(_.events).foreach { events =>
        events.length shouldBe 2751
      }
    }
  }
  
  test("eventsByColumnPath") {
    withTestConsole { storageConsole =>
      val events = storageConsole.eventsByColumnPath("epochs/count")
      events.length shouldBe 2751
    }
  }

}