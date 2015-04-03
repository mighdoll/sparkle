package nest.sg

import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.loader.FilesLoader
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.util.FutureAwait.Implicits._
import nest.sparkle.util.Resources

class TestStorageConsole extends FunSuite with Matchers with CassandraStoreTestConfig {
  val fileName = "epochs.csv"
  val filePath = Resources.filePathString(fileName)
  
  override def testConfigFile = Some("tests")

  def withTestConsole[T](fn: StorageConsole => T): T = {
    withTestDb { testDb =>
      withTestActors{ implicit system =>
        val complete = onLoadComplete(testDb, fileName)
        new FilesLoader(sparkleConfig, filePath, fileName, testDb, Some(false))
        complete.await
        val storageConsole = new StorageConsole {
          override val store = testDb
          override val executionContext = system.dispatcher
        }
        fn(storageConsole)
      }
    }
  }

  test("all column categories") {
    withTestConsole { storageConsole =>
      val allColumnCategories = storageConsole.allColumnCategories().toBlocking.toList
      allColumnCategories.toSet shouldBe Set("count", "p99", "p90")
    }
  }

  test("eventsByLeafDataSet") {
    withTestConsole { storageConsole =>
      val allEvents = storageConsole.eventsByLeafDataSet("epochs")
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

  test("dataByColumnPath") {
    withTestConsole { storageConsole =>
      val data = storageConsole.columnData("epochs/count")
      data.length shouldBe 2751
    }
  }

}