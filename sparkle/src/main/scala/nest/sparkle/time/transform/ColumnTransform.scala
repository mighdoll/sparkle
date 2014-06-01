package nest.sparkle.time.transform

import scala.concurrent.{ ExecutionContext, Future }

import spray.json._
import spray.json.DefaultJsonProtocol._

import nest.sparkle.store.{ Column, LongDoubleColumn, LongLongColumn }
import nest.sparkle.time.protocol.JsonDataStream

/** A function that constructs a JsonDataStream.  The JsonDataStream will transform a single
  * source column into a json output column when it s called.
  */
trait ColumnTransform {
  def apply[T, U]  // format: OFF
      (column: Column[T, U], transformParameters: JsObject)
      (implicit execution: ExecutionContext): JsonDataStream // format: ON
}

object StandardColumnTransform {
  /** return a future that will execute a transform on a each column in
    * a future set of transforms.
    */
  def runTransform(futureColumns: Future[Seq[Column[_, _]]], // format: OFF
      columnTransform:ColumnTransform, transformParameters:JsObject)
      (implicit execution: ExecutionContext):Future[Seq[JsonDataStream]] = { // format: ON
    futureColumns.map { columns =>
      columns.map { column =>
        columnTransform(column, transformParameters)
      }
    }
  }
  
}
