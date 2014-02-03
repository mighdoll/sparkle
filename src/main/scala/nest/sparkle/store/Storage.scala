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
trait Storage {
  /** return the dataset for the provided dataSet name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet]

  /** return a column from a columnPath e.g. "fooSet/barSet/columName". */
  def column[T,U](columnPath: String): Future[Column[T,U]] 
}

object Storage {
  /** return an instance of Storage, based on the storage class specified in the config file */
  def instantiateStorage(config:Config):Storage = {
    val storageClass = config.getString("storage")
    val storage = Instance.byName[Storage](storageClass)(config)
    storage
  }
  /** return an instance of Storage, based on the storage class specified in the config file */
  def instantiateWritableStorage(config:Config):WriteableStorage = {
    val storageClass = config.getString("writeable-storage")
    val storage = Instance.byName[WriteableStorage](storageClass)(config)
    storage
  }
}

