package nest.sparkle.time.protocol

import java.nio.ByteBuffer
import org.scalatest.FunSuite
import org.scalatest.Matchers
import spray.json.JsonFormat
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import nest.sparkle.store.Event
import nest.sparkle.time.protocol.TransformParametersJson.RawParametersFormat
import nest.sparkle.util.SparkleJsonProtocol._

class TestVariousTypes extends FunSuite with Matchers with CassandraStoreTestConfig
    with StreamRequestor {

  def testExplicitType[T: JsonFormat](columnName: String)(fn: T => Unit) {
    val columnPath = s"explicitTypes/$columnName"
    withLoadedFile("explicitTypes.csv") { (store, system) =>
      TestDataService.withTestService(store, system) { service =>
        val message = streamRequest("Raw", RawParameters[Long](), SelectString(columnPath))
        service.v1TypedRequest(message) { events: Seq[Seq[Event[Long, T]]] =>
          fn(events.head.head.value)
        }
      }
    }
  }

  test("deliver boolean value over protocol request") {
    testExplicitType[Boolean]("boo") { firstValue =>
      firstValue shouldBe true
    }
  }
  
  // TODO test delivery of short values
  
  test("deliver int value over protocol request") {
    testExplicitType[Int]("int") { firstValue =>
      firstValue shouldBe 2
    }
  }
  
  test("deliver long value over protocol request") {
    testExplicitType[Long]("lon") { firstValue =>
      firstValue shouldBe 3
    }
  }
  
  test("deliver double value over protocol request") {
    testExplicitType[Double]("dou") { firstValue =>
      firstValue shouldBe 4
    }
  }
  
  // TODO test delivery of char values
  
  test("deliver string value over protocol request") {
    testExplicitType[String]("str") { firstValue =>
      firstValue shouldBe "st"
    }
  }
  
  test("deliver json value over protocol request") {
    import spray.json._
    testExplicitType[JsValue]("jso") { firstValue =>
      val expected = """{ "js": 9 }""".parseJson
      firstValue shouldBe expected
    }
  }

  test("deliver blob value over protocol request") {
    import com.google.common.base.Charsets
    testExplicitType[ByteBuffer]("blo") { firstValue =>
      val expected = ByteBuffer.wrap("abc".getBytes(Charsets.UTF_8))
      firstValue shouldBe expected
    }
  }

}
