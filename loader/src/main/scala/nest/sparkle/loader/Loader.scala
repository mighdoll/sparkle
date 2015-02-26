/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */
package nest.sparkle.loader

import scala.language.existentials
import scala.util.Try

import nest.sparkle.store.Event
import nest.sparkle.util.Log

object Loader extends Log {
  /** a segment of every column in a record */
  type Events[T, U] = Seq[Event[T, U]]

  /** a segment of events from multiple columns, along with their types  */
  type TaggedBlock = Seq[TaggedSlice[_, _]]

  /** a segment of events (in DataArray format) from multiple columns, along with their types */
  type TaggedBlock2 = Seq[TaggedSlice2[_, _]]

  /** thrown if the source schema specifies an unimplemented key or value type */
  case class UnsupportedColumnType(msg: String) extends RuntimeException(msg)

  /** transform a source column slice into a potentially different slice */
  trait LoadingTransformer {
    def transform(source: TaggedBlock2): Try[TaggedBlock2]
  }

}

/** an update to a watcher about the latest value loaded */
case class ColumnUpdate[T](columnPath: String, latest: T)

