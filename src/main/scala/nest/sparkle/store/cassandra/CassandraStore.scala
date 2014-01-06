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
import scala.language._
import com.datastax.driver.core.{ Cluster, Session }
import nest.sparkle.util.RandomUtil
import com.datastax.driver.core.PreparedStatement
import rx.lang.scala.Observable
import nest.sparkle.store.cassandra.ObservableFuture._
import nest.sparkle.graph.Event
import nest.sparkle.graph.Storage
import nest.sparkle.graph.DataSet2
import nest.sparkle.graph.DataSet2
import nest.sparkle.graph.Column

case class AsciiString(val string: String) extends AnyVal
case class NanoTime(val nanos: Long) extends AnyVal
case class MilliTime(val millis: Long) extends AnyVal

object CassandraStore {
  def apply(contactHost: String) = new CassandraStore(contactHost)

  /** Convert a proposed name into a name that's safe to use as a cassandra table name (aka column family).
    * illegal characters are removed.  too long names are partially replaced with a random string
    * e.g. my.server.name.is.too.long.what.shall.i.store.latency.p99 will be replaced with
    * myvservervnamevi16RandomDigits16.  The caller is expected to maintain her own mapping from
    * proposed names to sanitized names.
    */
  protected[cassandra] def sanitizeTableName(proposedName: String): String = {
    val sanitized: Seq[Char] =
      proposedName.map {
        case c if c.isLetterOrDigit => c
        case _                      => 'v'
      }
    if (sanitized.length < 32) {
      sanitized.mkString
    } else {
      sanitized.take(16).mkString + RandomUtil.randomAlphaNum(16)
    }
  }
}

/** a Storage DAO for cassandra.  */
class CassandraStore(contactHost: String) extends Storage {
  lazy val catalog = ColumnCatalog(session)

  /** create a connection to the cassandra cluster */
  implicit lazy val session: Session = {
    val cluster = Cluster.builder()
      .addContactPoint(contactHost)
      .build()

    cluster.connect()
  }

  /** close the connection to cassandra.  */
  def close() = session.shutdown()

  /** Erase the column store, and recreate the core tables (synchronously: does not return until complete) */
  def formatLocalDb(keySpace:String = "events") {    
    session.execute(s"""
        DROP KEYSPACE IF EXISTS $keySpace"""
    )
    session.execute(s"""
        CREATE KEYSPACE $keySpace
        with replication = {'class': 'SimpleStrategy', 'replication_factor': 1}"""
    )
    session.execute(s"USE $keySpace")
    catalog.create()
  }

  /** return the dataset for the provided dataSet name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet2] = ???

  /** return a column from a fooSet/barSet/columName path */
  def column[T: CanSerialize, U: CanSerialize](columnPath: String): Future[Column[T, U]] = {
    val (dataSetName, columnName) = setAndColumn(columnPath)
    val column = SparseColumn[T, U](dataSetName, columnName, session, catalog)
    Future.successful(column)
  }

  /** return a column from a fooSet/barSet/columName path */
  def writeableColumn[T: CanSerialize, U: CanSerialize](columnPath: String): Future[WriteableColumn[T, U]] = {
    val (dataSetName, columnName) = setAndColumn(columnPath)
    val column = SparseColumn[T, U](dataSetName, columnName, session, catalog)
    Future.successful(column)
  }

  /** split a columnPath into a dataSet and column components */
  private def setAndColumn(columnPath: String): (String, String) = {
    val separator = columnPath.lastIndexOf("/")
    val dataSetName = columnPath.substring(0, separator)
    val columnName = columnPath.substring(separator + 1)
    assert (dataSetName.length > 0)
    assert (columnName.length > 0)
    (dataSetName, columnName)
  }

}
