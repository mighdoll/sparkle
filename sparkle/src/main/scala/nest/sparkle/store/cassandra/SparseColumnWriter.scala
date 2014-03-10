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
import SparseColumnWriter._

case class SparseWriterStatements(
  val insertOne: PreparedStatement,
  val deleteAll: PreparedStatement)

object SparseColumnWriter {
  /** constructor to create a SparseColumWriter */
  def apply[T: CanSerialize, U: CanSerialize](dataSetName: String,
                                              columnName: String, session: Session, catalog: ColumnCatalog) =
    new SparseColumnWriter[T, U](dataSetName, columnName, session, catalog)

  /** create columns for default data types */
  def createColumnTables(session: Session)(implicit execution: ExecutionContext):Future[Unit] = {
    createEmptyColumn[Long, Double](session)
  }

  /** create a column asynchronously (idempoentent) */
  protected def createEmptyColumn[T: CanSerialize, U: CanSerialize](session: Session) // format: OFF
      (implicit execution:ExecutionContext): Future[Unit] = { // format: ON
    val serials = serialInfo[T, U]()
    // cassandra storage types for the argument and value
    val argumentStoreType = serials.domain.columnType
    val valueStoreType = serials.range.columnType
    val tableName = serials.tableName
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
    created
  }

  /** return some serialization info for the types provided */
  protected def serialInfo[T: CanSerialize, U: CanSerialize](): SerializeInfo[T, U] = {
    val domainSerializer = implicitly[CanSerialize[T]]
    val rangeSerializer = implicitly[CanSerialize[U]]

    // cassandra storage types for the argument and value
    val argumentStoreType = domainSerializer.columnType
    val valueStoreType = rangeSerializer.columnType
    val tableName = argumentStoreType + "0" + valueStoreType

    SerializeInfo(domainSerializer, rangeSerializer, tableName)
  }

  /** holder for serialization info for given domain and range types */
  case class SerializeInfo[T, U](domain: CanSerialize[T], range: CanSerialize[U], tableName: String)
}

/** Manages a column of (argument,value) data pairs.  The pair is typically
  * a millisecond timestamp and a double value.
  */
class SparseColumnWriter[T: CanSerialize, U: CanSerialize](
    val dataSetName: String, val columnName: String, session: Session, catalog: ColumnCatalog) extends WriteableColumn[T, U] with PreparedStatements[SparseWriterStatements] with ColumnSupport {

  val serials = serialInfo[T, U]()

  /** create a cassandra table for a given sparse column */
  def create(description: String)(implicit executionContext: ExecutionContext): Future[Unit] = {
    // LATER check to see if table already exists first

    val entry = CatalogEntry(columnPath = columnPath, tableName = serials.tableName, description = description,
      domainType = serials.domain.nativeType, rangeType = serials.range.nativeType)

    val created = createEmptyColumn[T, U](session)
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

  val deleteAllStatement = """
    DELETE FROM bigint0double
    WHERE dataSet = ? AND column = ? AND rowIndex = ? 
    """

  private def makeStatements(): SparseWriterStatements = {
    SparseWriterStatements(
      insertOne = session.prepare(insertOneStatement),
      deleteAll = session.prepare(deleteAllStatement)
    )
  }
  
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

  /** delete all the column values in the column */
  def erase()(implicit executionContext: ExecutionContext): Future[Unit] = { // format: ON
    val statement = sparseColumnStatements.deleteAll.bind(Seq[Object](dataSetName, columnName, rowIndex): _*)
    session.executeAsync(statement).toFuture.map { _ => () }
  }  
  
  /** write one column value */ // format: OFF
  private def writeOne(event:Event[T,U])
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON

    val (argument, value) = serializeEvent(event)

    val statement = sparseColumnStatements.insertOne.bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex, argument, value): _*
    )
    session.executeAsync(statement).toFuture.map { _ => () }
  }
  
  /** write a bunch of column values in a batch */ // format: OFF
  private def writeMany(events:Iterable[Event[T,U]])
      (implicit executionContext:ExecutionContext): Future[Unit] = { // format: ON
    val statements = events.map { event =>
      val (argument, value) = serializeEvent(event)
      sparseColumnStatements.insertOne.bind(
        Seq[AnyRef](dataSetName, columnName, rowIndex, argument, value): _*
      )
    }
    
    val batch = new BatchStatement()
    batch.addAll(statements.toSeq.asJava)

    session.executeAsync(batch).toFuture.map { _ => () }
  }

  /** return a tuple of cassandra serializable objects for an event */
  private def serializeEvent(event: Event[T, U]): (AnyRef, AnyRef) = {
    val argument = serials.domain.serialize(event.argument)
    val value = serials.range.serialize(event.value)

    (argument, value)
  }

  private def sparseColumnStatements(): SparseWriterStatements = {
    preparedStatements(makeStatements)
  }

}

