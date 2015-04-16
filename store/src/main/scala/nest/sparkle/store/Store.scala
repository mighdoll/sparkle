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

/** An abstraction for a datastore that holds readable columns */
trait Store {
  //TODO: handle partial matches based on lookupKey?
  /** Return the entity paths associated with the given lookup key. If none, the Future
    * if failed with EntityNotFoundForLookupKey. */
  def entities(lookupKey: String): Future[Seq[String]]

  /** Return the specified entity's column paths. */
  def entityColumnPaths(entityPath: String): Future[Seq[String]]

  /** Return the specified leaf dataSet's column paths, where a leaf dataSet is
    * a dataSet with only columns (not other dataSets) as children */
  def leafDataSetColumnPaths(dataSet: String): Future[Seq[String]]

  /** return the dataset for the provided dataSet name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet]

  /** return a column from a columnPath e.g. "fooSet/barSet/columName". */
  def column[T, U](columnPath: String): Future[Column[T, U]]

  def close(): Unit
  
  /** allow callers to listen for write events per column */
  def writeListener:WriteListener
}

trait ReadWriteStore extends Store with WriteableStore

object Store {
  /** return an instance of Store, based on the store class specified in the config file */
  def instantiateStore(config: Config, writeListener: WriteListener): Store = {
    val storeClass = config.getString("store")
    val store = Instance.byName[Store](storeClass)(config, writeListener)
    store
  }

  /** return an instance of WriteableStore, based on the store class specified in the config file */
  def instantiateWritableStore(config: Config, writeNotifier: WriteNotifier): WriteableStore = {
    val storeClass = config.getString("writeable-store")
    val store = Instance.byName[WriteableStore](storeClass)(config, writeNotifier)
    store
  }

  /** return an instance of ReadWriteStore, based on the store class specified in the config file */
  def instantiateReadWriteStore(config: Config, writeNotifier: WriteNotifier): ReadWriteStore = {
    val storeClass = config.getString("read-write-store")
    val store = Instance.byName[ReadWriteStore](storeClass)(config, writeNotifier)
    store
  }

  case class MalformedColumnPath(msg: String) extends RuntimeException(msg)
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

/** Used when there aren't any entity paths associated with the given lookup key */
case class EntityNotFoundForLookupKey(lookupKey: String) extends RuntimeException(s"No entities found for $lookupKey")

/** Used when a data set cannot be found in the store */
case class DataSetNotFound(dataSetPath: String) extends RuntimeException(s"$dataSetPath does not exist")

/** the dataset subsystem is disabled */
case class DataSetNotEnabled() extends RuntimeException

/** Used when a column path cannot be found in the store */
case class ColumnNotFound(columnPath: String) extends RuntimeException(columnPath)

/** Used when the specified input, like a entity path or a leaf dataSet, has no columns in the store */
case class HasNoColumns(input: String) extends RuntimeException(s"$input does not have any columns")

/** Used when a column path can't be split into data set and column components */
case class MalformedColumnPath(msg: String) extends RuntimeException(msg)
