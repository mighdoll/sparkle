package nest.sparkle.time.protocol

import nest.sparkle.store.{ Event, WriteableStore }
import nest.sparkle.store.cassandra.CassandraTestConfig
import nest.sparkle.store.cassandra.serializers.{ BooleanSerializer, LongSerializer }
import org.scalatest.{ FunSuite, Matchers }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.DurationInt
import spray.util.pimpFuture
import nest.sparkle.store.cassandra.WriteableColumn
import nest.sparkle.store.cassandra.TestServiceWithCassandra
import spray.http.StatusCodes
import spray.json._
import nest.sparkle.time.protocol.ResponseJson.StreamsMessageFormat
import nest.sparkle.store.Store
import nest.sparkle.util.QuickTiming._
import scala.concurrent.forkjoin.ForkJoinPool
import scala.collection.parallel._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration
import akka.util.Timeout

class TestIntervalSumLarge extends FunSuite with Matchers with CassandraTestConfig with StreamRequestor with IntervalSumFixture {

  /* store a collection of MultiRecords into the Store */
  def storeMultiRecords // format: OFF
      (store:WriteableStore, records:Iterator[MultiRecord], dataSet:String)
      (implicit execution:ExecutionContext) { // format: ON

    val writeGroupSize = 1000 // write in batches to load faster

    /** write columns for all the records, unpacking the MutliRecords to columnar form, and batching by groupSize */
    records.grouped(writeGroupSize).foreach { group =>
      val a = group.map(_.a)
      val b = group.map(_.b)
      val one = group.map(_.one)
      val two = group.map(_.two)
      val three = group.map(_.three)
      val four = group.map(_.four)
      val five = group.map(_.five)
      val six = group.map(_.six)
      val times = group.map(_.millis)

      val itemSeqs = itemSets(times, a, b, one, two, three, four, five, six)
      val columns = writeableColumns("a", "b", "one", "two", "three", "four", "five", "six")
      val written = itemSeqs zip columns map {
        case (items, futureColumn) => futureColumn.flatMap(_.write(items))
      }

      Future.sequence(written).await
    }

    def makeItems(times: Seq[Long], values: Seq[Boolean]): Seq[Event[Long, Boolean]] =
      times zip values map { case (key, value) => Event(key, value) }

    def writeableColumn(columnName: String): Future[WriteableColumn[Long, Boolean]] =
      store.writeableColumn[Long, Boolean](s"$dataSet/$columnName")

    def writeableColumns(names: String*): Seq[Future[WriteableColumn[Long, Boolean]]] =
      names map writeableColumn

    def itemSets(times: Seq[Long], onOffs: Seq[Boolean]*): Seq[Seq[Event[Long, Boolean]]] =
      onOffs.map { booleans => makeItems(times, booleans) }
  }

  /** Load MultiRecord data into the store and summarize
    *
    * @param nodes number of entities (standin for e.g. users, machines, sensors) to store (+1 node for warmup)
    * @param records number of MultiValue records stored for each node
    * @param delta timegap between records
    * @param flipChance each Boolean in the MutliValue flips state with this probability
    * @param summary query summarizes to this period
    * @param warmups warm up the code path by making this number of queries
    * @param repeats query each node's data this number of times.
    * @param parallel number of queries to run in parallel
    * @param maxWait wait this long for each query to complete 
    */
  def intervalSumLargeTest( // format: OFF
      nodes: Int = 10,
      records: Int = 1000,
      delta: FiniteDuration = 1.hour,
      flipChance: Double = .01,
      summary: String = "1 month",
      warmups: Int = 3,
      repeats: Int = 10,
      parallel: Int = 10,
      maxWait: FiniteDuration = 20.seconds) { // format: ON

    withTestDb { testStore =>
      withTestActors { implicit system =>
        MultiIntervalSum.withTestService(testStore, system) { testService =>
          implicit val execution = system.dispatcher

          loadData(testStore)
          Range.inclusive(1, warmups).foreach { _ => oneQuery(testService, "node0") }
          runTestQueries(testService)
        }
      }
    }

    /** return a string to select all eight of the MultiRecord columns in a given dataset */
    def multiColumnPaths(dataSet: String): String = {
      s"""[
			    | ["$dataSet/one", "$dataSet/two", "$dataSet/three", "$dataSet/four", "$dataSet/five", "$dataSet/six"], 
			    | ["$dataSet/a", "$dataSet/b"]
				  ]""".stripMargin
    }

    /** load test data into the store */
    def loadData(testStore: WriteableStore)(implicit execution: ExecutionContext) {
      val numRecords = nodes * records
      val numItems = numRecords * 8
      val time = timed {
        Range.inclusive(0, nodes).foreach { nodeNumber =>
          val generator = GenerateOnOffIntervals.generate(flipChance, delta).take(records)
          storeMultiRecords(testStore, generator, s"node$nodeNumber")
        }
      }
      val itemsPerSecond = numItems / (time / 1000)
      println(s"loading $numRecords records ($numItems items). Total time: ${millisString(time)}.  ${numberString(itemsPerSecond)} items per second")
    }

    /** load data via api request, blocks until the request returns */
    def oneQuery(testService: TestDataService, node: String) {
      val columns = multiColumnPaths(node)
      val message = MultiIntervalSum.requestMessage(columns, summary)
      val result = testService.sendDataMessage(message, maxWait).await(Timeout(maxWait))
      result.status shouldBe StatusCodes.OK
      val streams = result.entity.asString.asJson.convertTo[StreamsMessage]
      streams.message.streams.size shouldBe 1
    }

    /** run the number of queries specified by the testParameters. The queries
      * are all IntervalSum requests of the entire range of data, summarized
      * into 1 minute partitions.
      */
    def runTestQueries(testService: TestDataService) {
      val time = timed {
        val nodesToRequest =
          for {
            _ <- Range.inclusive(1, repeats)
            node <- Range.inclusive(1, nodes)
          } yield {
            s"node$node"
          }

        // setup to run queries from their own thread pool
        val parallelRequests = nodesToRequest.toVector.par
        parallelRequests.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(parallel))

        parallelRequests.foreach { nodeName =>
          oneQuery(testService, nodeName)
        }
      }

      val queries = repeats * nodes
      val totalTime = millisString(time)
      val queriesPerSecond = numberString(queries / (time / 1000))
      println(s"query $queries times ($records records per query). Total time: $totalTime.  $queriesPerSecond queries per second")
    }
  }

  test("timed intervalSum on just a few records") {
    intervalSumLargeTest(nodes = 2, records = 100, delta = 1.hour, flipChance = .01,
      summary = "1 day", repeats = 2, parallel = 10, warmups = 2)
  }
  
  ignore("timed intervalSum on 10K records") {  // 10K is a bit more than 1 year of daily records
    intervalSumLargeTest(nodes = 1, records = 10000, delta = 1.hour, flipChance = .01,
      summary = "1 month", repeats = 200, parallel = 1, warmups = 0)
  }
  
  ignore("timed intervalSum on 100K records") {  // 10K is a bit more than 1 year of daily records
    intervalSumLargeTest(nodes = 1, records = 100000, delta = 1.hour, flipChance = .01,
      summary = "1 month", repeats = 10, parallel = 1, warmups = 0)
  }
  
  ignore("timed intervalSum on 10K records, 10 requests in parallel, 1000 times") {
    intervalSumLargeTest(nodes = 10, records = 10000, delta = 1.hour, flipChance = .01,
      summary = "1 month", repeats = 1000, parallel = 10, warmups = 2)
  }
}

