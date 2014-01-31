package nest.sparkle.time.protocol

import rx.lang.scala.Observable
import spray.json._

/** Contains an observable stream of json encoded arrays suitable for use in a protocol json data stream.  */
case class JsonDataStream(
  /** An observable that produces json encoded data to send in Streams or Update messages */
  dataStream: Observable[Seq[JsArray]],

  /** Format of the produced JsArrays */
  streamType: JsonStreamType,

  /** Optional name for the stream so that transforms that return multiple streams can label each stream */
  label: Option[String] = None // format: OFF
) // format: ON

