package nest.sparkle.time.transform

import scala.concurrent.ExecutionContext

import com.typesafe.config.Config

import spray.json._

import nest.sparkle.store.Column
import nest.sparkle.time.protocol.JsonDataStream

/** Trait for developer provided subclasses containing transforms that process columns
 *  of data */
trait CustomTransform extends ColumnTransform {
  /** a name used to match the `transform` field in the StreamRequest message to identify when
    * this record should be used. The namespace of `transform` strings is shared
    * across all requests, so subclasses are advised to override this with a unique name.
    */
  def name: String = this.getClass.getSimpleName

}

/** Implementations of CustomTransform must have a constructor that takes a single Config
  * parameter and must extend CustomTransform
  */
class ExampleCustomTransform(rootConfig: Config) extends CustomTransform {

  override def name: String = "MyStuff.MyCoolTransform"

  override def apply[T: JsonFormat, U: JsonWriter]  // format: OFF
      (column: Column[T, U], transformParameters: JsObject)
      (implicit execution: ExecutionContext): JsonDataStream = ??? // format: ON
} 