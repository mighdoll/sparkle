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

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.existentials
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success, Try}

import com.datastax.driver.core.{PreparedStatement, Session, SimpleStatement}
import com.typesafe.config.Config
import rx.lang.scala.Observable
import spray.caching.LruCache

import nest.sparkle.store.{ColumnPathFormat, ColumnNotFound, ColumnCategoryNotFound}
import nest.sparkle.store.cassandra.ObservableResultSet._
import nest.sparkle.util.GuavaConverters._
import nest.sparkle.util.OptionConversion._
import nest.sparkle.util.TryToFuture._
import nest.sparkle.util.Log

/** metadata about a column of data
  *
  * @param columnPath name of the data set and column, e.g. "server1/responseLatency/p99"
  * @param tableName cassandra table for the column e.g. "timestamp0double"
  * @param description description of the column (for developer UI documentation)
  * @param keyType type of key elements, e.g. "NanoTime" for nanosecond timestamps
  * @param valueType type of value elements, e.g. "Double" for double event
  * @param bucketSize bucket size in seconds, for data partitioning in cassandra
  * @param firstBucketStart first possible starting bucket
  */
case class CassandraCatalogEntry(
  columnPath: String,
  tableName: String,
  description: String,
  keyType: String,
  valueType: String,
  bucketSize: Long,
  firstBucketStart: Long)

case class CatalogStatements(addCatalogEntry: PreparedStatement,
                             catalogInfo: PreparedStatement)

/** Manages a table of CatalogEntry rows in cassandra.  Each CatalogEntry references
  * the cassandra table holding the column data, as well as other
  * metadata about the column like the type of data that is stored in the column.
  */
case class ColumnCatalog(sparkleConfig: Config, columnTypes: ColumnTypes,
    columnPathFormat: ColumnPathFormat, session: Session,
    cassandraConsistency: CassandraConsistency)
    extends PreparedStatements[CatalogStatements] with Log {

  val tableName = ColumnCatalog.tableName

  /** insert or overwrite a catalog entry */
  val addCatalogEntryStatement = s"""
      INSERT INTO $tableName
      (columnCategory, tableName, description, keyType, valueType, bucketSize, firstBucketStart)
      VALUES (?, ?, ?, ?, ?, ?, ?);
      """

  val catalogInfoStatement = s"""
      SELECT tableName, keyType, valueType, bucketSize, firstBucketStart FROM $tableName
      WHERE columnCategory = ?;
    """

  val allColumnCategoriesStatement = s"""
      SELECT columnCategory FROM $tableName
      LIMIT 50000000;
    """

  /** LRU cache used to track column categories that this instance has read/written
    * from/to cassandra, so we can avoid duplicate reads/writes */
  val columnCategoriesCache = LruCache[CatalogInfo](sparkleConfig.getConfig("sparkle-store-cassandra")
    .getInt("column-categories-max-cache-size"))

  /** clears the column categories cache */
  def format() = {
    columnCategoriesCache.clear()
  }

  /** store metadata about a column in Cassandra */ // format: OFF
  def writeCatalogEntry(entry: CassandraCatalogEntry)
      (implicit executionContext:ExecutionContext): Future[CatalogInfo] = { // format: ON
    columnPathFormat.columnCategory(entry.columnPath) match {
      case Success(columnCategory) =>
        cachedWriteCatalogEntry(columnCategory, entry)
      case Failure(exception) => Future.failed(exception)
    }
  }

  /** caches column categories that this instance has written to cassandra,
    * so we can avoid duplicate writes */
  private def cachedWriteCatalogEntry(columnCategory : String, entry: CassandraCatalogEntry)
      (implicit executionContext:ExecutionContext): Future[CatalogInfo] = columnCategoriesCache(columnCategory) { // format: ON
    writeCatalogEntryToCassandra(columnCategory, entry)
  }

  /** writes a column category to cassandra */
  private def writeCatalogEntryToCassandra(columnCategory : String, entry: CassandraCatalogEntry)
      (implicit executionContext:ExecutionContext): Future[CatalogInfo] = { // format: ON
    log.info(s"writing column category: $columnCategory")
    val entryFields = entry.productIterator.map { elem => elem.asInstanceOf[AnyRef]}.toArray
    entryFields(0) = columnCategory
    val statement = catalogStatements.addCatalogEntry.bind(entryFields: _*)
    val result = session.executeAsync(statement).toFuture.map { _ =>
      CatalogInfo(entry.tableName, columnTypes.stringToTypeTag(entry.keyType),
        columnTypes.stringToTypeTag(entry.valueType), entry.bucketSize, entry.firstBucketStart)
    }
    result.onFailure { case error => log.error("writing catalog entry failed", error)}
    result
  }

  /** return metadata about a given columnPath (based on data previously stored with writeCatalogEntry) */
  def catalogInfo(columnPath: String) // format:OFF
      (implicit executionContext: ExecutionContext): Future[CatalogInfo] = { // format: ON
    columnPathFormat.columnCategory(columnPath) match {
      case Success(columnCategory) =>
        log.debug(s"catalogInfo found for columnPath: $columnPath")
        cachedCatalogInfo(columnCategory, columnPath)
      case Failure(exception) =>
        log.debug(s"catalogInfo not found for columnPath: $columnPath")
        Future.failed(ColumnNotFound(columnPath).initCause(exception))
    }
  }

  /** caches column categories that this instance has read from cassandra,
    * so we can avoid duplicate reads */
  def cachedCatalogInfo(columnCategory : String, columnPath: String) // format:OFF
      (implicit executionContext: ExecutionContext): Future[CatalogInfo] = columnCategoriesCache(columnCategory) { // format: ON
    readCatalogInfoFromCassandra(columnCategory, columnPath)
  }

  /** reads a column category from cassandra */
  def readCatalogInfoFromCassandra(columnCategory: String, columnPath: String) // format:OFF
      (implicit executionContext: ExecutionContext): Future[CatalogInfo] = { // format: ON
    log.info(s"reading column category: $columnCategory")
    val statement = catalogStatements.catalogInfo.bind(columnCategory)
    // result should be a single row containing a three strings:
    for {
      resultSet <- session.executeAsync(statement).toFuture
      row <- Option(resultSet.one()).toFutureOr(ColumnNotFound(columnPath).initCause(ColumnCategoryNotFound(columnCategory)))
    } yield {
      val tableName = row.getString(0)
      val keyType = columnTypes.stringToTypeTag(row.getString(1))
      val valueType = columnTypes.stringToTypeTag(row.getString(2))
      val bucketSize = row.getLong(3)
      val firstBucketStart = row.getLong(4)
      CatalogInfo(tableName, keyType, valueType, bucketSize, firstBucketStart)
    }
  }

  // TODO: periodically read from cassandra and cache the results?
  /** return all column categories from cassandra as a Future */
  def allColumnCategoriesFuture() // format:OFF
      (implicit executionContext: ExecutionContext): Future[Seq[String]] = { // format: ON
    for {
      resultSet <- session.executeAsync(allColumnCategoriesSimpleStatement).toFuture
      rows <- Try(resultSet.all()).toFuture
    } yield {
      rows.asScala.map { row =>
        row.getString(0)
      }
    }
  }

  /** return all column categories from cassandra as an Observable */
  def allColumnCategoriesObservable() // format:OFF
      (implicit executionContext: ExecutionContext): Observable[String] = { // format: ON
    val rows = session.executeAsync(allColumnCategoriesSimpleStatement).observerableRows()
    rows.map { row =>
      row.getString(0)
    }
  }

  private def allColumnCategoriesSimpleStatement =
    new SimpleStatement(allColumnCategoriesStatement).setConsistencyLevel(cassandraConsistency.read)
  // for cassandra driver 2.2.0+
  //    session.newSimpleStatement(allColumnCategoriesStatement).setConsistencyLevel(cassandraConsistency.read)

  lazy val catalogStatements: CatalogStatements = {
    preparedStatements(makeStatements)
  }

  /** prepare some cql statements */
  def makeStatements(): CatalogStatements = {
    CatalogStatements(
      addCatalogEntry = session.prepare(addCatalogEntryStatement).setConsistencyLevel(cassandraConsistency.write),
      catalogInfo = session.prepare(catalogInfoStatement).setConsistencyLevel(cassandraConsistency.read)
    )
  }
}

case class CatalogInfo(tableName: String, keyType: TypeTag[_], valueType: TypeTag[_],
  bucketSize: Long, firstBucketStart: Long)

object ColumnCatalog {

  val tableName = "column_categories"

  /** Create the table using the session passed.
    *
    * @param session Session to use.
    */
  def create(session: Session): Unit = {
    session.execute(s"""
      CREATE TABLE IF NOT EXISTS $tableName (
        columnCategory text,
        tableName text,
        description text,
        keyType text,
        valueType text,
        bucketSize bigint,
        firstBucketStart bigint,
        PRIMARY KEY(columnCategory)
      );"""
    )
  }

}

