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

import scala.concurrent.{ ExecutionContext, Future }

import rx.lang.scala.Observable

import nest.sparkle.store.DataSet
import nest.sparkle.store.Column
import nest.sparkle.util.TryToFuture._

case class CassandraDataSet(store: CassandraStoreReader, name: String) extends DataSet {
  implicit def execution: ExecutionContext = store.execution

  /** return a column in this dataset (or FileNotFound) */
  def column[T, U](columnName: String): Future[Column[T, U]] = {
    store.column(s"$name/$columnName")  // TODO untested    
  }

  /**
   * return all child columns
   *
   * @return Observable of full path of any child columns of this DataSet.
   */
  def childColumns: Observable[String] = {
    childEntries.filter(_.isColumn).map(_.childPath)
  }

  /**
   * return all child datasets
   *
   * @return Observable of any child DataSets of this DataSet.
   */
  def childDataSets: Observable[DataSet] = {
    childEntries.filter(! _.isColumn).map{entry => CassandraDataSet(store, entry.childPath)}
  }

  private def childEntries: Observable[DataSetCatalogEntry] = {
    for {
      catalog <- Observable.from(store.tryDataSetCatalog.toFuture)
      entries <- catalog.childrenOfParentPath(name)
    } yield entries
  }
}
