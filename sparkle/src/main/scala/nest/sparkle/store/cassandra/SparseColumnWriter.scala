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

package nest.sparkle.store.cassandra

import com.datastax.driver.core.Session
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import nest.sparkle.util.GuavaConverters._
import com.datastax.driver.core.PreparedStatement
import rx.lang.scala.Observable
import com.datastax.driver.core.Row
import com.datastax.driver.core.BatchStatement
import scala.collection.JavaConverters._
import nest.sparkle.store.Event
import nest.sparkle.store.Column
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.Log
import SparseColumnWriter._
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory
import nest.sparkle.store.cassandra.ColumnTypes.serializationInfo
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Executors

object SparseColumnWriter extends PrepareTableOperations {
  case class InsertOne(override val tableName: String) extends TableOperation
  case class DeleteAll(override val tableName: String) extends TableOperation

  val log = Logger(LoggerFactory.getLogger(getClass.getName)) // SCALA how to extend Log in both class and companion?

  override val prepareStatements = List(
    (InsertOne -> insertOneStatement _),
    (DeleteAll -> deleteAllStatement _)
  )

  /** constructor to create a SparseColumnWriter */
  def instance[T: CanSerialize, U: CanSerialize](dataSetName: String, columnName: String, // format: OFF 
      catalog: ColumnCatalog, dataSetCatalog: DataSetCatalog)
      (implicit session: Session, execution:ExecutionContext):Future[SparseColumnWriter[T,U]] = { // format: ON

    val writer = new SparseColumnWriter[T, U](dataSetName, columnName, catalog, dataSetCatalog)
    writer.updateCatalog().map { _ => writer }
  }

  /** create columns for default data types */
  def createColumnTables(session: Session)(implicit execution: ExecutionContext): Future[Unit] = {
    val futures = ColumnTypes.supportedColumnTypes.map { serialInfo =>
      createEmptyColumn(session)(serialInfo.domain, serialInfo.range, execution)
    }
    Future.sequence(futures).map { _ => () }
  }

  /** create a column asynchronously (idempotent) */
  private def createEmptyColumn[T: CanSerialize, U: CanSerialize](session: Session) // format: OFF
      (implicit execution:ExecutionContext): Future[Unit] = { // format: ON
    val serialInfo = serializationInfo[T, U]()
    // cassandra storage types for the argument and value
    val argumentStoreType = serialInfo.domain.columnType
    val valueStoreType = serialInfo.range.columnType
    val tableName = serialInfo.tableName
    assert(tableName == CassandraStore.sanitizeTableName(tableName))

    val createTable = s"""  
      CREATE TABLE IF NOT EXISTS "$tableName" (
        dataSet ascii,
        rowIndex int,
        column ascii,
        argument $argumentStoreType,
        value $valueStoreType,
        PRIMARY KEY((dataSet, column, rowIndex), argument)
      ) WITH COMPACT STORAGE
      """
    val created = session.executeAsync(createTable).toFuture.map { _ => () }
    created.onFailure{ case error => log.error("createEmpty failed", error) }
    created
  }

  private def deleteAllStatement(tableName: String): String = s"""
      DELETE FROM $tableName
      WHERE dataSet = ? AND column = ? AND rowIndex = ? 
      """

  private def insertOneStatement(tableName: String): String = s"""
      INSERT INTO $tableName
      (dataSet, column, rowIndex, argument, value)
      VALUES (?, ?, ?, ?, ?)
      """

}

/** Manages a column of (argument,value) data pairs.  The pair is typically
  * a millisecond timestamp and a double value.
  */
protected class SparseColumnWriter[T: CanSerialize, U: CanSerialize]( // format: OFF
    val dataSetName: String, val columnName: String, 
    catalog: ColumnCatalog, dataSetCatalog: DataSetCatalog)(implicit session: Session) 
  extends WriteableColumn[T, U] with ColumnSupport with Log { // format: ON

  val serialInfo = serializationInfo[T, U]()
  val tableName = serialInfo.tableName
  
  /** create a catalog entries for this given sparse column */
  protected def updateCatalog(description: String = "no description")(implicit executionContext: ExecutionContext): Future[Unit] = {
    // LATER check to see if table already exists first

    val entry = CassandraCatalogEntry(columnPath = columnPath, tableName = tableName, description = description,
      domainType = serialInfo.domain.nativeType, rangeType = serialInfo.range.nativeType)

    val result =
      for {
        _ <- catalog.writeCatalogEntry(entry)
        _ <- dataSetCatalog.addColumnPath(entry.columnPath)
      } yield { () }

    result.onFailure { case error => log.error("create column failed", error) }
    result
  }

  /** write a bunch of column values in a batch */ // format: OFF
  def write(items:Iterable[Event[T,U]])
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON
    val events = items.toSeq
    log.trace(s"write() to $tableName $columnPath  events: $events ")
    writeMany(events)
  }

  /** delete all the column values in the column */
  def erase()(implicit executionContext: ExecutionContext): Future[Unit] = { // format: ON
    val deleteAll = statement(DeleteAll(tableName)).bind(Seq[Object](dataSetName, columnName, rowIndex): _*)
    session.executeAsync(deleteAll).toFuture.map { _ => () }
  }  
  
  
  /** write a bunch of column values in a batch */ // format: OFF
  private def writeMany(events:Iterable[Event[T,U]])
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON
    val group = events.map { event =>
      val (argument, value) = serializeEvent(event)
      statement(InsertOne(tableName)).bind(
        Seq[AnyRef](dataSetName, columnName, rowIndex, argument, value): _*
      )
    }

    val batch = new BatchStatement()
    batch.addAll(group.toSeq.asJava)

    session.executeAsync(batch).toFuture.map { _ => () }
  }

  /** return a tuple of cassandra serializable objects for an event */
  private def serializeEvent(event: Event[T, U]): (AnyRef, AnyRef) = {
    val argument = serialInfo.domain.serialize(event.argument)
    val value = serialInfo.range.serialize(event.value)

    (argument, value)
  }

}

