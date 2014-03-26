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

/** 
 * metadata about a column of data 
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
                                  rangeType: String
                                )

case class CatalogStatements(addCatalogEntry: PreparedStatement, tableForColumn: PreparedStatement)

/** Manages a table of CatalogEntry rows in cassandra.  Each CatalogEntry references
  * the cassandra table holding the column data, as well as other
  * metadata about the column like the type of data that is stored in the column.
  */
case class ColumnCatalog(session: Session) extends PreparedStatements[CatalogStatements] 
{
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
    
  /** store catalog entry in Cassandra */ // format: OFF
  def writeCatalogEntry(entry: CassandraCatalogEntry)
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON
    val entryFields = entry.productIterator.map { elem => elem.asInstanceOf[AnyRef] }.toSeq
    val statement = catalogStatements.addCatalogEntry.bind(entryFields: _*)
    session.executeAsync(statement).toFuture.map{ _ => }
  }

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

  lazy val catalogStatements: CatalogStatements = {
    preparedStatements(makeStatements)
  }

  /** prepare the */
  def makeStatements(): CatalogStatements = {
    CatalogStatements(
      addCatalogEntry = session.prepare(addCatalogEntryStatement),
      tableForColumn = session.prepare(tableForColumnStatement)
    )
  }
}

object ColumnCatalog {
  
  val catalogTable = "catalog"

  /**
   * Create the table using the session passed.
   * 
   * @param session Session to use. Shadows instance variable.
   */
  def create(session: Session) {
    session.execute(s"""
      CREATE TABLE $catalogTable (
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

