/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.store.ram

import nest.sparkle.store.Store
import scala.concurrent.Future
import nest.sparkle.store.DataSet
import nest.sparkle.store.Column
import scala.collection.mutable
import nest.sparkle.util.OptionConversion._
import java.io.FileNotFoundException
import nest.sparkle.store.WriteableStore
import nest.sparkle.store.cassandra.CanSerialize
import nest.sparkle.store.cassandra.WriteableColumn
import scala.reflect.runtime.universe._

/** A java heap resident database of Columns */
class WriteableRamStore extends Store {
  private val columns = mutable.Map[String, RamColumn[_, _]]()

  /** return the dataset for the provided dataSet name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet] = ???

  /** return a column from a columnPath e.g. "fooSet/barSet/columName". */
  def column[T, U](columnPath: String): Future[Column[T, U]] = {
    val optTypedColumn = columns.get(columnPath).map { _.asInstanceOf[Column[T, U]] }
    optTypedColumn.toFutureOr(new FileNotFoundException())
  }

  /** return a WriteableColumn for the given columnPath.  (returned as a future 
   *  for compatibility with other slower Storage types) */
  def writeableColumn[T: TypeTag: Ordering, U: TypeTag](columnPath: String) // format: OFF
      : Future[WriteableColumn[T, U]] = { // format: ON
    val ramColumn = new WriteableRamColumn[T, U]("foo")
    columns += columnPath -> ramColumn
    Future.successful(ramColumn)
  }
}
