package nest.sparkle.time.protocol

import scala.reflect.runtime.universe._

import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.util._
import spray.json._

import nest.sparkle.store.Event
import nest.sparkle.store.cassandra.ArbitraryColumn.arbitraryEvent
import nest.sparkle.time.protocol.TestKeyValueRanges.minMaxEvents
import nest.sparkle.time.protocol.RequestJson.StreamRequestMessageFormat
import nest.sparkle.time.transform.{KeyValueRanges, MinMax}
import nest.sparkle.time.transform.KeyValueRangesJson
import nest.sparkle.time.transform.KeyValueRangesJson.KeyValueRangesFormat
import nest.sparkle.util.RandomUtil.randomAlphaNum

class TestKeyValueRangesRequest extends PreloadedRamStore with StreamRequestor with TestDataService {

  override def readWriteStore = writeableRamStore

  /** create a new column in the test RAM store and return its columnPath */
  def makeColumn[T: TypeTag, U: TypeTag](prefix: String, events: List[Event[T, U]]): String = {
    implicit val serializeKey = recoverCanSerialize.tryCanSerialize[T](typeTag[T]).get
    implicit val serializeValue = recoverCanSerialize.tryCanSerialize[U](typeTag[U]).get
    val columnName = prefix + "/" + randomAlphaNum(4)
    val column = writeableRamStore.writeableColumn[T, U](columnName).await
    column.write(events)
    columnName
  }

  test("KeyValueRanges calculates the min and max of the keys and values of arbitrary long,double columns") {
    forAll { events: List[Event[Long, Double]] =>
      val columnName = makeColumn("V1Protocol.KeyValueRanges", events)
      val requestMessage = streamRequest("KeyValueRanges", JsObject(), SelectString(columnName))
      Post("/v1/data", requestMessage) ~> v1protocol ~> check {
        val data = TestDataService.streamDataJson(response).head
        data.length shouldBe 2
        if (events.isEmpty) {
          data shouldBe KeyValueRangesJson.Empty
        } else {
          val keyValueRanges = data.toJson.convertTo[KeyValueRanges[Long, Double]]
          val (keyMin, keyMax, valueMin, valueMax) = minMaxEvents(events)
          keyValueRanges shouldBe KeyValueRanges(keyRange = MinMax(keyMin, keyMax), valueRange = MinMax(valueMin, valueMax))
        }
      }
    }
  }
}