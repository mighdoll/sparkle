package nest.sg

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.loader.FilesLoader
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.util.Resources
import nest.sparkle.util.FutureAwait.Implicits._

class TestStorageConsole extends FunSuite with Matchers with CassandraStoreTestConfig {
  val fileName = "epochs.csv"
  val filePath = Resources.filePathString(fileName)
  
  override def testConfigFile = Some("tests")

  def withTestConsole[T](fn: ConcreteStorageConsole => T): T = {
    withTestDb { testDb =>
      withTestActors{ implicit system =>
        val complete = onLoadComplete(testDb, fileName)
        FilesLoader(sparkleConfig, filePath, fileName, testDb)
        complete.await
        val storageConsole = new ConcreteStorageConsole(testDb, system.dispatcher)
        fn(storageConsole)
      }
    }
  }

  test("all columns") {
    withTestConsole { storageConsole =>
      val allColumns = storageConsole.allColumns().toBlocking.toList
      allColumns.toSet shouldBe Set("epochs/count", "epochs/p99", "epochs/p90")
    }
  }

  // TODO figure out what to do with the dataset catalog
  /*
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
  */
  
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