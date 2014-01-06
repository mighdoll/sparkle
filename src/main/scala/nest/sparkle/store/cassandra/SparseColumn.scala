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

import com.datastax.driver.core.Session
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import nest.sparkle.util.GuavaConverters._
import com.datastax.driver.core.PreparedStatement
import rx.lang.scala.Observable
import nest.sparkle.store.cassandra.ObservableResultSet._
import com.datastax.driver.core.Row
import com.datastax.driver.core.BatchStatement
import scala.collection.JavaConverters._
import nest.sparkle.graph.Event
import nest.sparkle.graph.Column

case class SparseColumnStatements(
  val insertOne: PreparedStatement,
  val readAll: PreparedStatement,
  val deleteAll: PreparedStatement)

/** Manages a column of (argument,value) data pairs.  The pair is typically
  * a nanosecond timestamp a double value.
  */
case class SparseColumn[T: CanSerialize, U: CanSerialize](
    dataSetName:String, columnName: String, session: Session, catalog: ColumnCatalog
    ) extends Column[T, U] with WriteableColumn[T,U] with PreparedStatements[SparseColumnStatements] {
  def name = columnName
  lazy val columnPath = dataSetName + "/" + columnName
  
  private val domainSerializer = implicitly[CanSerialize[T]]
  private val rangeSerializer = implicitly[CanSerialize[U]]


  // cassandra storage types for the argument and value
  private val argumentStoreType = domainSerializer.columnType
  private val valueStoreType = rangeSerializer.columnType  
  private val tableName =  argumentStoreType + "0" + valueStoreType
  
  /** create a cassandra table for a given sparse column */
  def create(description: String)(implicit executionContext: ExecutionContext): Future[Unit] = {
    assert(tableName == CassandraStore.sanitizeTableName(tableName))
    // LATER check to see if table already exists.  Consider race condition in creating vs. checking..

    val createTable = s"""  
      CREATE TABLE "$tableName" (
        dataSet ascii,
        rowIndex int,
        column ascii,
        argument $argumentStoreType,
        value $valueStoreType,
        PRIMARY KEY((dataSet, column, rowIndex), argument)
      ) WITH COMPACT STORAGE
    """
    val created = session.executeAsync(createTable).toFuture
    val entry = CatalogEntry(columnPath = columnPath, tableName = tableName, description = description,
      domainType = domainSerializer.nativeType, rangeType = rangeSerializer.nativeType)

    val catalogged: Future[Unit] = catalog.writeCatalogEntry(entry)
    created.zip(catalogged).map{ _ => () }
  }

  // LATER generate the queries for the appropriate table types dynamically
  // (for now we hard code the bigint0double table for bigint, Double events)

  val insertOneStatement = """
    INSERT INTO bigint0double
    (dataSet, column, rowIndex, argument, value)
    VALUES (?, ?, ?, ?, ?)
    """

  val readAllStatement = """
    SELECT argument, value FROM bigint0double
    WHERE dataSet = ? AND column = ? AND rowIndex = ? 
    """

  val deleteAllStatement = """
    DELETE FROM bigint0double
    WHERE dataSet = ? AND column = ? AND rowIndex = ? 
    """

  private def makeStatements(): SparseColumnStatements = {
    SparseColumnStatements(
      insertOne = session.prepare(insertOneStatement),
      readAll = session.prepare(readAllStatement),
      deleteAll = session.prepare(deleteAllStatement)
    )
  }

  /** return a successful future if the column exists */
  def exists(implicit context:ExecutionContext):Future[Unit]= {
    catalog.tableForColumn(columnPath).map{_ => ()} 
  }
  
  private val rowIndex = 0.asInstanceOf[AnyRef]
  
  /** write a bunch of column values in a batch */ // format: OFF
  def write(items:Iterable[Event[T,U]])
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON
    val events = items.toSeq
    if (events.length == 1) {
      writeOne(events.head)
    } else {
      writeMany(events)
    }
  }
  
  /** read all the column values from the column */
  private def readAll()(implicit executionContext:ExecutionContext):Observable[Event[T,U]] = { // format: ON
    val statement = sparseColumnStatements.readAll.bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex): _*)

    def rowDecoder(row: Row): Event[T,U] = {
      val argument = domainSerializer.fromRow(row, 0)
      val value = rangeSerializer.fromRow(row, 1)
      Event(argument, value)
    }

    val rows = session.executeAsync(statement).observerableRows
    rows map rowDecoder
  }

  /** delete all the column values in the column */
  def erase()(implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON
    val statement = sparseColumnStatements.deleteAll.bind(Seq[Object](dataSetName, columnName, rowIndex): _*)
    session.executeAsync(statement).toFuture.map { _ => () }
  }
  
    /** read a slice of events from the column */      // format: OFF
  def readRange(start:Option[T] = None, end:Option[T] = None) 
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    if (start.isEmpty && end.isEmpty) {
      readAll()
    } else {
      ???
    }
  }

  /** read a range of events from the column */      // format: OFF
  def readBefore(start:T, maxResults:Long = Long.MaxValue) 
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    ???
  }
  
  /** read a range of events from the column */      // format: OFF
  def readAfter(start:T, maxResults:Long = Long.MaxValue) 
      (implicit execution: ExecutionContext): Observable[Event[T,U]] = { // format: ON
    ???
  }
  
    /** write one column value */ // format: OFF
  private def writeOne(event:Event[T,U])
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON

    val (argument,value) = serializeEvent(event)
    
    val statement = sparseColumnStatements.insertOne.bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex, argument, value): _*
    )
    session.executeAsync(statement).toFuture.map { _ => () }
  }
  
  /** write a bunch of column values in a batch */ // format: OFF
  private def writeMany(events:Iterable[Event[T,U]])
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON
    val statements = events.map { event =>
      val (argument,value) = serializeEvent(event)
      sparseColumnStatements.insertOne.bind(
        Seq[AnyRef](dataSetName, columnName, rowIndex, argument, value): _*
      )
    }
    val batch = new BatchStatement()
    batch.addAll(statements.toSeq.asJava)

    session.executeAsync(batch).toFuture.map { _ => () }
  }


  /** return a tuple of cassandra serializable objects for an event */
  private def serializeEvent(event:Event[T,U]):(AnyRef, AnyRef) = {
    val argument = domainSerializer.serialize(event.argument)
    val value = rangeSerializer.serialize(event.value)

    (argument, value)
  }


  
  private def sparseColumnStatements(): SparseColumnStatements = {
    preparedStatements(makeStatements)
  }
  
  

}

