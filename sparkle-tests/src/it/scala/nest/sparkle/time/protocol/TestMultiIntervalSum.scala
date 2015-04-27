package nest.sparkle.time.protocol

import org.scalatest.{ FunSuite, Matchers }
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import com.typesafe.config.Config
import nest.sparkle.store.{ReadWriteStore, Store, Column, Event}
import spray.json.JsObject
import scala.concurrent.ExecutionContext
import spray.json.JsString
import scala.concurrent.Future
import spray.json._
import spray.util.pimpFuture
import scala.collection.JavaConverters._
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import spray.json.DefaultJsonProtocol._
import scala.util.control.Exception._
import MultiParamsJson.multiParamsJson
import nest.sparkle.time.transform.ColumnGroup
import spray.http.HttpResponse
import nest.sparkle.util.StringToMillis.IsoDateString
import scala.concurrent.duration._
import MultiIntervalSum._
import akka.actor.ActorSystem

object MultiIntervalSum {
  def requestMessage(columnPaths: String, partBySize: String = "1 minute"): String =
    s"""{
      |  "messageType": "StreamRequest",
      |  "message": {
      |    "sources": [
      |      { "selector" : "MultiSelect",
			|		    "selectorParameters" : {
	  	|			     "columnPaths": $columnPaths
      |        }
      |      } 
      |    ],
      |    "transform": "OnOffIntervalSum",
      |    "transformParameters": {
      |      "partBySize" : "$partBySize"
      |    }
      |  }
      |}""".stripMargin

  def withTestService[T](readWriteStore:ReadWriteStore, system: ActorSystem)(fn: TestDataService => T): T = {
    val service = new TestServiceWithCassandra(readWriteStore, system) {
      override def configOverrides = {
        val selectors = Seq(s"${classOf[MultiSelect].getName}").asJava
        super.configOverrides :+ s"$sparkleConfigName.custom-selectors" -> selectors
      }
    }
    try {
      fn(service)
    } finally {
      service.close()
    }
  }
}

class TestMultiIntervalSum extends FunSuite with Matchers with CassandraStoreTestConfig with StreamRequestor with IntervalSumFixture {
  def withMultiSelect(csvFile: String, columnPaths: String)(fn: Seq[Seq[Event[Long, Seq[Long]]]] => Unit): Unit = {
    val message = requestMessage(columnPaths)

    withLoadedFile(s"$csvFile.csv") { (store, system) =>
      withTestService(store, system) { service =>
        val response = service.sendDataMessage(message).await
        val data = TestDataService.dataFromStreamsResponse[Seq[Long]](response)
        fn(data)
      }
    }

  }

  /** return a collection of longs in UTC millis from a sequence of utc time strings */
  def toMillis(dateTimes: Seq[String]): Seq[Long] = {
    dateTimes.map { dateTime =>
      (dateTime + "Z").toMillis
    }
  }

  test("multi on-off specified intervals") {
    withMultiSelect("_intervals-multi",
      """[["one", "two", "three", "four", "five", "six"], ["a", "b"]]""") { data =>
        data.head.map(_.key) shouldBe toMillis(Seq("2014-07-06T00:00:00.000", "2014-07-06T00:01:00.000", "2014-07-06T00:02:00.000"))
        data.head.map(_.value) shouldBe Seq(
          Seq(1.minute.toMillis, 1.minute.toMillis),
          Seq(1.minute.toMillis, 1.minute.toMillis),
          Seq(0, 1.minute.toMillis)
        )
      }
  }

  test("multi on-off with two non overlapping") {
    withMultiSelect("_intervals-non-overlapped",
      """[["a", "b"]]""") { data =>
        data.head.head shouldBe Event("2014-07-06T00:04:00.000Z".toMillis, Seq(30.seconds.toMillis))
      }
  }

  test("multi on-off with just one on/off") {
    withMultiSelect("_intervals-simple-on-off", """[["one"]]""") { data =>
      data.head.head shouldBe Event("2014-07-06T00:00:00.000Z".toMillis, Seq(1.minute.toMillis))
    }
  }

  test("multi on-off with just one on/on") {
    withMultiSelect("_intervals-simple-on-on", """[["one"]]""") { data =>
      data.head.head shouldBe Event("2014-07-06T00:00:00.000Z".toMillis, Seq(1.minute.toMillis))
    }
  }

}

case class MultiParams(columnPaths: Seq[Seq[String]])

object MultiParamsJson extends DefaultJsonProtocol {
  implicit val multiParamsJson = jsonFormat1(MultiParams)
}

class MultiSelect(rootConfig: Config, store: Store) extends CustomSourceSelector {
  /** match selector parameters of the form: { columnPaths: [[a,b,c], [foo,bar]] } */
  object SelectorParameters {
    def unapply(params: JsObject)(implicit execution: ExecutionContext): Option[MultiParams] = {
      nonFatalCatch opt params.convertTo[MultiParams]
    }
  }

  override def selectColumns(selectorParameters: JsObject)(implicit execution: ExecutionContext) // format: OFF
      :Future[Seq[ColumnGroup]] = { // format: ON
    selectorParameters match {
      case SelectorParameters(MultiParams(groups)) =>
        val futureGroups: Seq[Future[ColumnGroup]] =
          groups.map { columnPaths =>
            val futureColumns = columnPaths.map { columnPath => store.column(columnPath) }
            Future.sequence(futureColumns).map { columns => ColumnGroup(columns) }
          }
        Future.sequence(futureGroups)
      case x =>
        Future.failed(MalformedSourceSelector(x.toString))
    }
  }

  override def name = getClass.getSimpleName
}
