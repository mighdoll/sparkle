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

package nest.sparkle.store.cassandra

import nest.sparkle.store.Storage
import java.nio.file.Path
import scala.concurrent.Future
import nest.sparkle.store.DataSet
import nest.sparkle.store.Column

case class FileSystemStorage(root:Path) extends Storage{
  /** return the dataset for the provided dataSet name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet] = ???

  /** return a column from a columnPath like fooSet/barSet/columName*/
  def column[T, U](columnPath: String): Future[Column[T, U]] = ???

}
