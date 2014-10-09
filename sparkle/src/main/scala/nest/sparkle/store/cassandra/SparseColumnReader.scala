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
import nest.sparkle.store.Column
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import rx.lang.scala.Observable
import nest.sparkle.store.Event
import com.datastax.driver.core.Row
import com.datastax.driver.core.PreparedStatement
import nest.sparkle.store.cassandra.ObservableResultSet._
import scala.reflect.runtime.universe._
import nest.sparkle.util._
import nest.sparkle.util.Log
import com.datastax.driver.core.BoundStatement
import nest.sparkle.store.OngoingEvents
import rx.lang.scala.Subject
import scala.util.Random
import scala.concurrent.duration._
import nest.sparkle.store.cassandra.SparseColumnReaderStatements._

object SparseColumnReader extends Log {
  def instance[T, U](dataSetName: String, columnName: String, catalog: ColumnCatalog, // format: OFF
      writeListener:WriteListener, preparedSession:PreparedSession) 
      (implicit execution: ExecutionContext): Future[SparseColumnReader[T,U]] = { // format: ON

    val columnPath = ColumnSupport.constructColumnPath(dataSetName, columnName)

    /** create a SparseColumnReader of the supplied X,Y type. The cast it to the
      * (presumably _) type of instance()s T,U parameters.
      */
    def makeReader[X, Y](key: CanSerialize[X], value: CanSerialize[Y]): SparseColumnReader[T, U] = {
      val typed = new SparseColumnReader(dataSetName, columnName, catalog, writeListener, preparedSession)(key, value)
      typed.asInstanceOf[SparseColumnReader[T, U]]
    }

    /** create a reader of the appropriate type */
    def reader(catalogInfo: CatalogInfo): SparseColumnReader[T, U] = {
      catalogInfo match { // TODO capture types for key,value independently to avoid combinatorial
        case LongDoubleSerializers(KeyValueSerializers(key, value))  => makeReader(key, value)
        case LongLongSerializers(KeyValueSerializers(key, value))    => makeReader(key, value)
        case LongIntSerializers(KeyValueSerializers(key, value))     => makeReader(key, value)
        case LongBooleanSerializers(KeyValueSerializers(key, value)) => makeReader(key, value)
        case LongStringSerializers(KeyValueSerializers(key, value))  => makeReader(key, value)
        case LongJsValueSerializers(KeyValueSerializers(key, value)) => makeReader(key, value)
        case _                                                       => ???
      }
    }

    for {
      catalogInfo <- catalog.catalogInfo(columnPath)
    } yield {
      reader(catalogInfo)
    }
  }

}

import SparseColumnReader._


/** read only access to a cassandra source event column */
class SparseColumnReader[T: CanSerialize, U: CanSerialize]( // format: OFF 
    val dataSetName: String, 
    val columnName: String, 
    catalog: ColumnCatalog, 
    writeListener:WriteListener, 
    prepared: PreparedSession)
      extends Column[T, U] with ColumnSupport { // format: ON

  def name: String = columnName

  private val keySerializer = implicitly[CanSerialize[T]]
  private val valueSerializer = implicitly[CanSerialize[U]]
  def keyType: TypeTag[T] = keySerializer.typedTag
  def valueType: TypeTag[U] = valueSerializer.typedTag

  val serialInfo = ColumnTypes.serializationInfo()(keySerializer, valueSerializer)
  val tableName = serialInfo.tableName

  /** read a slice of events from the column */      // format: OFF
  override def readRange(start:Option[T] = None, end:Option[T] = None, limit:Option[Long] = None)
      (implicit execution: ExecutionContext): OngoingEvents[T,U] = { // format: ON
    (start, end) match {
      case (None, None)             => readAll()
      case (Some(start), Some(end)) => readBoundedRange(start, end)
      case (Some(start), None)      => readFromStart(start)
      case _                        => ???
    }
  }

  /** read all the column values from the column */
  private def readAll()(implicit executionContext: ExecutionContext): OngoingEvents[T, U] = { // format: ON
    log.trace(s"readAll from $tableName $columnPath")
    val readStatement = prepared.statement(ReadAll(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex): _*)

    OngoingEvents(initial = readEventRows(readStatement), ongoing = ongoingRead())
  }

  private def readFromStart(start: T) // format: OFF
      (implicit execution:ExecutionContext): OngoingEvents[T,U] = { // format: ON
    OngoingEvents(readFromStartCurrent(start), ongoingRead())
  }

  /** read the data at or after a given start key,
    * returning an Observable that completes with a single read of row data
    * from C*
    */
  private def readFromStartCurrent(start: T) // format: OFF
      (implicit execution:ExecutionContext): Observable[Event[T,U]] = { // format: ON
    log.trace(s"readFromStartCurrent from $tableName $columnPath $start")
    val readStatement = prepared.statement(ReadFromStart(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex,
        start.asInstanceOf[AnyRef]): _*)
    readEventRows(readStatement)
  }

  private def handleColumnUpdate(columnUpdate: ColumnUpdate[T]) // format: OFF
      (implicit execution:ExecutionContext): Observable[Event[T,U]] = { // format: ON
    log.trace(s"handleColumnUpdate received $columnUpdate")
    readFromStartCurrent(columnUpdate.start)
  }

  /** listen for writes, and trigger a read each time new data is available.
    * Return an observable that never completes (except if there's an error).
    */
  private def ongoingRead()(implicit executionContext: ExecutionContext): Observable[Event[T, U]] = {
    writeListener.listen(columnPath).flatMap { columnUpdate: ColumnUpdate[T] =>
      handleColumnUpdate(columnUpdate)
    }
  }

  private def justListen() = {
    writeListener.listen(columnPath)
  }

  private def readBoundedRange(start: T, end: T) // format: OFF
      (implicit execution:ExecutionContext): OngoingEvents[T, U] = { // format: ON
    log.trace(s"readRange from $tableName $columnPath $start $end")
    val readStatement = prepared.statement(ReadRange(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex,
        start.asInstanceOf[AnyRef], end.asInstanceOf[AnyRef]): _*)

    OngoingEvents(readEventRows(readStatement), Observable.empty)
  }

  private def rowDecoder(row: Row): Event[T, U] = {
    log.trace(s"rowDecoder: $row")
    val argument = keySerializer.fromRow(row, 0)
    val value = valueSerializer.fromRow(row, 1)
    Event(argument.asInstanceOf[T], value.asInstanceOf[U])
  }

  private def readEventRows(statement: BoundStatement) // format: OFF
      (implicit execution:ExecutionContext): Observable[Event[T, U]] = { // format: ON
    val rows = prepared.session.executeAsync(statement).observerableRows
    rows map rowDecoder
  }

}
