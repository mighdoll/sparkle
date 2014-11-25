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

package nest.sparkle.store

import scala.concurrent.Future
import com.typesafe.config.Config
import nest.sparkle.util.Instance
import nest.sparkle.store.cassandra.WriteListener
import nest.sparkle.store.cassandra.WriteNotifier

/** An abstraction for a datastore that holds readable columns */
trait Store {
  /** return the dataset for the provided dataSet name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet]

  /** return a column from a columnPath e.g. "fooSet/barSet/columName". */
  def column[T, U](columnPath: String): Future[Column[T, U]]

  def close(): Unit
}

object Store {
  /** return an instance of Store, based on the store class specified in the config file */
  def instantiateStore(config: Config, writeListener: WriteListener): Store = {
    val storeClass = config.getString("store")
    val store = Instance.byName[Store](storeClass)(config, writeListener)
    store
  }

  /** return an instance of Store, based on the store class specified in the config file */
  def instantiateWritableStore(config: Config, writeNotifier: WriteNotifier): WriteableStore = {
    val storeClass = config.getString("writeable-store")
    val store = Instance.byName[WriteableStore](storeClass)(config, writeNotifier)
    store
  }

  /** split a columnPath into a dataSet and column components */
  def setAndColumn(columnPath: String): (String, String) = {
    val separator = columnPath.lastIndexOf("/")
    separator match {
      case -1                              => ("default", columnPath)
      case 0 if columnPath.tail.length > 0 => ("default", columnPath.tail)
      case 0                               => throw MalformedColumnPath(columnPath)
      case n =>
        val dataSetName = columnPath.substring(0, separator)
        val columnName = columnPath.substring(separator + 1)
        assert(dataSetName.length > 0)
        assert(columnName.length > 0)
        (dataSetName, columnName)
    }
  }
}

case class DataSetNotFound(name: String) extends RuntimeException(name)

case class ColumnNotFound(column: String) extends RuntimeException(column)

case class MalformedColumnPath(msg: String) extends RuntimeException(msg)
