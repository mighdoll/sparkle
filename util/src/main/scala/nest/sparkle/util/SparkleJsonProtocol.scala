package nest.sparkle.util

import java.nio.ByteBuffer

import spray.json._

object SparkleJsonProtocol extends DefaultJsonProtocol {

  /** For json formatting a ByteBuffer */
  implicit def byteBufferFormat = new RootJsonFormat[ByteBuffer] {
    def write(buf: ByteBuffer) = {
      JsArray(buf.array().map(_.toJson):_*)
    }
    def read(value: JsValue) = value match {
      case JsArray(elements) => ByteBuffer.wrap(elements.map(_.convertTo[Byte]).toArray[Byte])
      case x                 => deserializationError(s"Expected Array as JsArray, but got $x")
    }
  }
}
