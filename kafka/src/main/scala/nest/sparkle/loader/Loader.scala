package nest.sparkle.loader

import nest.sparkle.store.Event
import nest.sparkle.loader.kafka.TaggedSlice
import rx.lang.scala.Observable

object Loader {
  /** a segment of every column in a record */
  type Events[T, U] = Seq[Event[T, U]]

  /** a segment of events from multiple columns, along with their types  */
  type TaggedBlock = Seq[TaggedSlice[_, _]]

  /** thrown if the source schema specifies an unimplemented key or value type */
  case class UnsupportedColumnType(msg: String) extends RuntimeException(msg)

  /** transform a source column slice into a potentially different slice */
  trait LoadingTransformer {
    def transform(source: TaggedBlock): TaggedBlock
  }
}

/** an update to a watcher about the latest value loaded */
case class ColumnUpdate[T](columnPath: String, latest: T)

