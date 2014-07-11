package nest.sparkle.time.transform

import scala.concurrent.{ ExecutionContext, Future }

import spray.json._
import spray.json.DefaultJsonProtocol._

import nest.sparkle.store.{ Column, LongDoubleColumn, LongLongColumn }
import nest.sparkle.time.protocol.JsonDataStream

/** A function that constructs a JsonDataStream.  The JsonDataStream will transform a single
  * source column into a json output column when its dataStream is subscribed.
  */
trait ColumnTransform {
  def apply[T, U]  // format: OFF
      (column: Column[T, U], transformParameters: JsObject)
      (implicit execution: ExecutionContext): JsonDataStream // format: ON
}

/** A function that constructs a JsonDataStream.  The JsonDataStream will transform multiple
  * source columns into a json output column when its dataStream is subscribed.
  */
trait MultiColumnTransform {
  def apply  // format: OFF
      (column: Seq[Column[_,_]], transformParameters: JsObject)
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

  /** return a future that will execute a transform on all columns at once */
  def runMultiColumnTransform(futureColumns: Future[Seq[Column[_, _]]], // format: OFF
      multiColumnTransform:MultiColumnTransform, transformParameters:JsObject)
      (implicit execution: ExecutionContext):Future[Seq[JsonDataStream]] = { // format: ON
    futureColumns.map { columns =>
      Seq(multiColumnTransform(columns, transformParameters))
    }
  }

}
