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

import scala.collection.mutable
import scala.concurrent.Future

import nest.sparkle.store.{Column, ColumnNotFound, DataSet, DataSetNotFound, Store}
import nest.sparkle.store.cassandra.WriteableColumn
import nest.sparkle.util.OptionConversion.OptionFuture
import scala.reflect.runtime.universe._

/** A java heap resident database of Columns */
class WriteableRamStore extends Store {
  private val columns = mutable.Map[String, RamColumn[_, _]]()

  /** return the dataset for the provided name name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet] = {
    if (dataSetColumnPaths(name).isEmpty) {
      Future.failed(DataSetNotFound(name))
    } else {
      Future.successful(RamDataSet(this, name))
    }
  }

  /** return a column from a columnPath e.g. "fooSet/barSet/columName". */
  def column[T, U](columnPath: String): Future[Column[T, U]] = {
    val optTypedColumn = columns.get(columnPath).map { _.asInstanceOf[Column[T, U]] }
    optTypedColumn.toFutureOr(ColumnNotFound(columnPath))
  }

  /** return a WriteableColumn for the given columnPath.  (returned as a future
   *  for compatibility with other slower Storage types) */
  def writeableColumn[T: TypeTag, U: TypeTag](columnPath: String) // format: OFF
      : Future[WriteableColumn[T, U]] = { // format: ON
    val ramColumn = new WriteableRamColumn[T, U]("foo")
    columns += columnPath -> ramColumn
    Future.successful(ramColumn)
  }

  /**
   * Return the columnPaths of the children of the DataSet
   * @param name DataSet to return children for
   * @return Seq of ColumnPaths of children.
   */
  private[ram] def dataSetColumnPaths(name: String): Seq[String] = {
   columns.keys.filter {columnPath =>
      val (dataSetName, _) = Store.setAndColumn(columnPath)
      dataSetName == name
    }.toSeq
  }

  /** free memory we might have been hanging on to */
  def close(): Unit = {
    columns.clear()
  }

}

