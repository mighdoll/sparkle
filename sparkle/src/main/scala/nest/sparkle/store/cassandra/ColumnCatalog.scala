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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.datastax.driver.core.Session
import com.datastax.driver.core.PreparedStatement
import nest.sparkle.store.ColumnNotFound
import nest.sparkle.util.GuavaConverters._
import nest.sparkle.util.OptionConversion._
import nest.sparkle.util.Log
import nest.sparkle.store.cassandra.ObservableResultSet._

import scala.language.existentials
import scala.reflect.runtime.universe._
import nest.sparkle.util.TaggedKeyValue
import rx.lang.scala.Observable

/** metadata about a column of data
  *
  * @param columnPath name of the det and column, e.g. "server1.responseLatency/p99"
  * @param tableName cassandra table for the column e.g. "timestamp0double"
  * @param description description of the column (for developer UI documentation)
  * @param domainType type of domain elements, e.g. "NanoTime" for nanosecond timestamps
  * @param rangeType type of range elements, e.g. "Double" for double event
  */
case class CassandraCatalogEntry(
  columnPath: String,
  tableName: String,
  description: String,
  domainType: String,
  rangeType: String)

case class CatalogStatements(addCatalogEntry: PreparedStatement,
                             tableForColumn: PreparedStatement,
                             catalogInfo: PreparedStatement)

/** Manages a table of CatalogEntry rows in cassandra.  Each CatalogEntry references
  * the cassandra table holding the column data, as well as other
  * metadata about the column like the type of data that is stored in the column.
  */
case class ColumnCatalog(session: Session) extends PreparedStatements[CatalogStatements]
    with Log {
  val catalogTable = ColumnCatalog.catalogTable

  /** insert or overwrite a catalog entry */
  val addCatalogEntryStatement = s"""
      INSERT INTO $catalogTable
      (columnPath, tableName, description, domainType, rangeType)
      VALUES (?, ?, ?, ?, ?);
      """

  val tableForColumnStatement = s"""
      SELECT tableName FROM $catalogTable
      WHERE columnPath = ?;
    """

  val catalogInfoStatement = s"""
      SELECT tableName, domainType, rangeType FROM $catalogTable
      WHERE columnPath = ?;
    """

  /** store metadata about a column in Cassandra */ // format: OFF
  def writeCatalogEntry(entry: CassandraCatalogEntry)
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON
    log.info(s"writing catalog entry: $entry")
    val entryFields = entry.productIterator.map { elem => elem.asInstanceOf[AnyRef] }.toSeq
    val statement = catalogStatements.addCatalogEntry.bind(entryFields: _*)
    val result = session.executeAsync(statement).toFuture.map{ _ => }
    result.onFailure { case error => log.error("writing catalog entry failed", error) }
    result
  }

  /** return metadata about a given columnPath (based on data previously stored with writeCatalogEntry */
  def catalogInfo(columnPath: String) // format:OFF
  (implicit executionContext: ExecutionContext): Future[CatalogInfo] = { // format: ON
    val statement = catalogStatements.catalogInfo.bind(columnPath)

    // result should be a single row containing a three strings:
    for {
      resultSet <- session.executeAsync(statement).toFuture
      row <- Option(resultSet.one()).toFutureOr(ColumnNotFound(columnPath))
    } yield {
      val tableName = row.getString(0)
      val domainType = CanSerialize.stringToTypeTag(row.getString(1))
      val rangeType = CanSerialize.stringToTypeTag(row.getString(2))
      CatalogInfo(tableName, domainType, rangeType)
    }
  }

  // TODO I think we can get rid of this now
  /** return the cassandra table name for a given column */
  def tableForColumn(columnPath: String) // format: OFF
      (implicit executionContext: ExecutionContext): Future[String] = { // format: ON
    val statement = catalogStatements.tableForColumn.bind(columnPath)

    // result should be a single row containing a single string: the table name for the column
    for {
      resultSet <- session.executeAsync(statement).toFuture
      row <- Option(resultSet.one()).toFutureOr(ColumnNotFound(columnPath))
    } yield {
      row.getString(0)
    }
  }

  def allColumns() // format:OFF
  (implicit executionContext: ExecutionContext): Observable[String] = { // format: ON
    val allColumnsStatement = s"""
      SELECT columnPath FROM $catalogTable
      LIMIT 50000000;
    """

    val rows = session.executeAsync(allColumnsStatement).observerableRows
    // result should be rows containing a single string: the columnPath
    rows.map { row =>
      row.getString(0)
    }
  }

  lazy val catalogStatements: CatalogStatements = {
    preparedStatements(makeStatements)
  }

  /** prepare some cql statements */
  def makeStatements(): CatalogStatements = {
    CatalogStatements(
      addCatalogEntry = session.prepare(addCatalogEntryStatement),
      tableForColumn = session.prepare(tableForColumnStatement),
      catalogInfo = session.prepare(catalogInfoStatement)
    )
  }
}

case class CatalogInfo(tableName: String, keyType: TypeTag[_], valueType: TypeTag[_]) extends TaggedKeyValue

object ColumnCatalog {

  val catalogTable = "catalog"

  /** Create the table using the session passed.
    *
    * @param session Session to use.
    */
  def create(session: Session): Unit = {
    session.execute(s"""
      CREATE TABLE IF NOT EXISTS $catalogTable (
        columnPath text,
        tableName text,
        description text,
        domainType text,
        rangeType text,
        PRIMARY KEY(columnPath)
      );"""
    )
  }

}

