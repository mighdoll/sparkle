package nest.sparkle.store.cassandra

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import org.scalacheck.Test.Passed
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, Matchers}
import org.scalacheck.{Test, Prop, Arbitrary, Gen}

import nest.sparkle.datastream.DataArray
import nest.sparkle.measure.DummySpan
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.store.{DataSetNotEnabled, ColumnNotFound, Event}
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.FutureAwait.Implicits._
import nest.sparkle.util.StringToMillis._

class TestCassandraStore
  extends FunSuite
          with Matchers
          with PropertyChecks
          with CassandraStoreTestConfig {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def testKeySpace = "testcassandrastore"

  override def configOverrides: List[(String, Any)] =
    super.configOverrides :+
    (s"$sparkleConfigName.sparkle-store-cassandra.replication-factor" -> replicationFactor)

  private def replicationFactor = 1 // ensure replication factor so we can validate

  /** read and write a single event */
  def testOneEvent[T: CanSerialize : Arbitrary, U: CanSerialize : Arbitrary]() {
    withTestDb { store =>
      withTestColumn[T, U](store) { (writeColumn, testColumnPath) =>
        val eventMaker = ArbitraryColumn.arbitraryEvent[T, U]
        val event = eventMaker.arbitrary.sample.get

        writeColumn.write(event :: Nil).await

        val readColumn = store.column[T, U](testColumnPath).await
        val read = readColumn.readRangeOld(None, None)
        val results = read.initial.toBlocking.single
        results shouldBe event
      }
    }
  }

  /** read and write a single event */
  def testOneEventA[T: CanSerialize : Arbitrary, U: CanSerialize : Arbitrary]() {
    withTestDb { store =>
      withTestColumn[T, U](store) { (writeColumn, testColumnPath) =>
        val eventMaker = ArbitraryColumn.arbitraryEvent[T, U]
        val event = eventMaker.arbitrary.sample.get

        val keyType = implicitly[CanSerialize[T]].typedTag
        implicit val keyClass = ClassTag[T](keyType.mirror.runtimeClass(keyType.tpe))
        val valueType = implicitly[CanSerialize[U]].typedTag
        implicit val valueClass = ClassTag[U](valueType.mirror.runtimeClass(valueType.tpe))
        val pair = DataArray[T, U](Array[T](event.argument), Array[U](event.value))

        writeColumn.writeData(pair).await
        val readColumn = store.column[T, U](testColumnPath).await

        val read = readColumn.readRange(parentSpan = Some(DummySpan))
        val results = read.initial.toBlocking.single
        results should have length 1
      }
    }
  }

  test("create event schema and catalog") {
    // check keyspace exists and has expected replication factor
    def validateKeySpace(store: CassandraReaderWriter) {
      val resultRows = store.session.execute( s"""
          SELECT strategy_options FROM system.schema_keyspaces where keyspace_name = '$testKeySpace'
          """
      )
      val rows = resultRows.all.asScala
      rows.length shouldBe 1
      val strategy = rows(0).getString(0)
      val dcInfo = sparkleConfig.getStringList("sparkle-store-cassandra.data-centers")
        .asScala.toSeq.map{ dataCenter => s"""  "$dataCenter":"$replicationFactor"  """.trim }.mkString("{", ", ", "}")
      strategy shouldBe dcInfo
    }

    // check expected tables exist
    def validateTables(store: CassandraReaderWriter) {
      val resultRows = store.session.execute( s"""
          SELECT columnFamily_name FROM system.schema_columnfamilies where keyspace_name = '$testKeySpace'
          """
      )
      val rows = resultRows.all.asScala
      val tables = rows.map(_.getString(0)).toSet
      tables shouldBe Set(
        "bigint0bigint", "bigint0boolean", "bigint0double", "bigint0int", "bigint0text",
        "column_categories", "entity_catalog", "dataset_catalog"
      )
    }

    withTestDb { store =>
      validateKeySpace(store)
      validateTables(store)
    }
  }

  test("missing column returns ColumnNotFound") {
    val badPath = "foo/notAColumn"
    withTestDb { store =>
      val column = store.column[Int, Double](badPath)
      val done = column.recover { case ColumnNotFound(columnPath) => columnPath shouldBe badPath
      }
      done.await
    }
  }

  test("read+write one long-int item") {
    testOneEventA[Long, Int]()
  }

  test("read+write one long-double item") {
    testOneEventA[Long, Double]()
  }

  test("read+write one long-string item") {
    testOneEventA[Long, String]()
  }

  test("read+write many long double events") {
    withTestDb { implicit store =>
      forAll(Gen.chooseNum(100, 30000), minSuccessful(5)) { rowCount =>
        withTestColumn[Long, Double](store) { (writeColumn, testColumnPath) =>
          val readColumn = store.column[Long, Double](testColumnPath).await
          //          writeColumn.erase().await // intermittently fails. race in cassandra? // for now runnng one iteration per column
          val events = Range(0, rowCount).map { index =>
            Event(index.toLong, index.toDouble)
          }.toIterable

          writeColumn.write(events).await
          val read = readColumn.readRangeOld(None, None)
          val results = read.initial.toBlocking.toList
          results.length shouldBe rowCount
          results.zipWithIndex.foreach { case (item, index) =>
            item shouldBe Event(index, index)
          }
        }
      }
    }
  }

  test("read+write many long double events with DataArray") {
    withTestDb { implicit store =>
      val prop = Prop.forAllNoShrink(Gen.chooseNum(100, 30000)) { rowCount =>
        withTestColumn[Long, Double](store) { (writeColumn, testColumnPath) =>
          val readColumn = store.column[Long, Double](testColumnPath).await
          //          writeColumn.erase().await // intermittently fails. race in cassandra? // for now runnng one iteration per column
          val events = Range(0, rowCount).map { index =>
            Event(index.toLong, index.toDouble)
          }.toIterable
          
          val testArray = {
            val keys = events.map(_.argument).toArray
            val values = events.map(_.value).toArray
            DataArray(keys,values)
          }

          writeColumn.writeData(testArray).await
          val read = readColumn.readRange(parentSpan = Some(DummySpan))
          val resultParts = read.initial.toBlocking.toList

          val resultArray = resultParts.reduce(_ ++ _)
          resultArray.length shouldBe rowCount
          resultArray shouldBe testArray
        }
      }
      val result = Test.check(prop)(_.withMinSuccessfulTests(5))
      result.status shouldBe Passed
    }
  }

  // TODO figure out what to do with the dataset catalog
  ignore("multi-level dataset column") {
    withTestDb { store =>
      val parts = Array("a", "b", "c", "d", "e", "column")
      val paths = parts.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn = store.writeableColumn[Long, Double](paths.last).await

      // Each part except for the last should have a single dataset child
      paths.dropRight(1).sliding(2).foreach {
        case Array(parent, child) =>
          val root = store.dataSet(parent).await
          val childDataSets = root.childDataSets.toBlocking.toList
          childDataSets.length shouldBe 1
          childDataSets(0).name shouldBe child
      }

      // The last dataset path should have one child that is a column
      paths.takeRight(2).sliding(2).foreach {
        case Array(parent, child) =>
          val root = store.dataSet(parent).await
          val columns = root.childColumns.toBlocking.toList
          columns.length shouldBe 1
          columns(0) shouldBe child
      }
    }
  }

  ignore("multi-level dataset with 2 columns") {
    withTestDb { store =>
      val parts1 = Array("a", "b", "c", "d", "e", "columnZ")
      val paths1 = parts1.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn1 = store.writeableColumn[Long, Double](paths1.last).await

      val parts2 = Array("a", "b", "c", "d", "e", "columnA")
      val paths2 = parts2.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn2 = store.writeableColumn[Long, Double](paths2.last).await

      // Each part except for the last should have a single dataset child
      paths1.dropRight(1).sliding(2).foreach {
        case Array(parent, child) =>
          val root = store.dataSet(parent).await
          val childDataSets = root.childDataSets.toBlocking.toList
          childDataSets.length shouldBe 1
          childDataSets(0).name shouldBe child
      }

      // The last dataset path should have two children that are columns
      paths1.takeRight(2).dropRight(1).foreach { parent =>
        val root = store.dataSet(parent).await
        val columns = root.childColumns.toBlocking.toList
        columns.length shouldBe 2
        // Note that writeColumn1 should sort *after* writeColumn2.
        columns(0) shouldBe paths2.last
        columns(1) shouldBe paths1.last
      }
    }
  }

  ignore("multi-level dataset with 2 datasets at the second level") {
    withTestDb { store =>
      val parts1 = Array("a", "b", "c1", "column")
      val paths1 = parts1.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn1 = store.writeableColumn[Long, Double](paths1.last).await

      val parts2 = Array("a", "b", "c2", "column")
      val paths2 = parts2.scanLeft("") {
        case ("", x)   => x
        case (last, x) => last + "/" + x
      }.tail
      val writeColumn2 = store.writeableColumn[Long, Double](paths2.last).await

      // "a/b" should have two children, "a/b/c1" and "a/b/c2"
      val root = store.dataSet("a/b").await
      val childDataSets = root.childDataSets.toBlocking.toList
      childDataSets.length shouldBe 2
      childDataSets(0).name shouldBe "a/b/c1"
      childDataSets(1).name shouldBe "a/b/c2"

      // Ensure c level datasets have one child column
      Array("a/b/c1", "a/b/c2").foreach { parent =>
        val root = store.dataSet(parent).await
        val columns = root.childColumns.toBlocking.toList
        columns.length shouldBe 1
        columns(0) shouldBe (parent + "/" + parts1.last)
      }
    }
  }

  test("DataSet catalog is currently disabled") {
    withTestDb { store =>
      val result = store.dataSet("foo")
      result.failed.await shouldBe DataSetNotEnabled()
    }
  }

  test("read first key in column") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val column = store.column[Long,Double]("simple-events/seconds").await
      implicit val span = DummySpan
      val lastKey = column.firstKey().await
      lastKey shouldBe Some("2014-12-01T00:00:00.000".toMillis)
    }
  }

  test("read last key in column") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val column = store.column[Long,Double]("simple-events/seconds").await
      implicit val span = DummySpan
      val key = column.lastKey().await
      key shouldBe Some("2014-12-01T02:00:00.000".toMillis)
    }
  }

  test("read count of all items in column") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val column = store.column[Long,Double]("simple-events/seconds").await
      val count = column.countItems().await
      count shouldBe 5
    }
  }

  test("read count of items in column with start") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val column = store.column[Long,Double]("simple-events/seconds").await
      val start = "2014-12-01T00:40:00.000".toMillis
      val count = column.countItems(start = Some(start)).await
      count shouldBe 3
    }
  }

  test("read count of items in column with start and end") {
    withLoadedFile("simple-events.csv") { (store, system) =>
      val column = store.column[Long,Double]("simple-events/seconds").await
      val start = "2014-12-01T00:40:00.000".toMillis
      val end = "2014-12-01T02:00:00.000".toMillis
      val count = column.countItems(start = Some(start), end = Some(end)).await
      count shouldBe 2
    }
  }

}
