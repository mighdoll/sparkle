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

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import rx.lang.scala.Observable

import com.datastax.driver.core.{BoundStatement, Row}

import nest.sparkle.core.OngoingData
import nest.sparkle.datastream.DataArray
import nest.sparkle.store.{Column, ColumnUpdate, WriteListener, WriteEvent, WriteNotifier, Event, OngoingEvents}
import nest.sparkle.store.cassandra.SparseColumnReaderStatements._
import nest.sparkle.measure.{DummySpan, Span}
import nest.sparkle.util.Exceptions.NYI
import nest.sparkle.util.Log
import nest.sparkle.util.ObservableFuture._

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


/** read only access to a cassandra source event column */
class SparseColumnReader[T: CanSerialize, U: CanSerialize] ( // format: OFF
    val dataSetName: String, 
    val columnName: String, 
    catalog: ColumnCatalog, 
    writeListener:WriteListener, 
    prepared: PreparedSession )
      extends Column[T, U] with ColumnSupport { // format: ON
  
  import SparseColumnReader.log

  def name: String = columnName

  private val keySerializer = implicitly[CanSerialize[T]]
  private val valueSerializer = implicitly[CanSerialize[U]]
  implicit def keyType: TypeTag[T] = keySerializer.typedTag
  implicit def valueType: TypeTag[U] = valueSerializer.typedTag
  implicit val keyClass = ClassTag[T](keyType.mirror.runtimeClass(keyType.tpe))
  implicit val valueClass = ClassTag[U](valueType.mirror.runtimeClass(valueType.tpe))

  val serialInfo = ColumnTypes.serializationInfo()(keySerializer, valueSerializer)
  val tableName = serialInfo.tableName


  /** read a slice of events from the column */
  override def readRange  // format: OFF
      ( optStart: Option[T] = None,
        optEnd: Option[T] = None,
        optLimit: Option[Long] = None,
        parentSpan: Option[Span] )
      ( implicit execution: ExecutionContext)
      : OngoingData[T, U] = { // format: ON

    implicit val span = parentSpan.getOrElse(DummySpan) // TODO pass a nonOptional span to readRange

    (optStart, optEnd) match {
      case (None, None)             => readAll()
      case (Some(start), Some(end)) => readBoundedRange(start, end)
      case (Some(start), None)      => readFromStart(start)
      case _                        => ???
    }
  }

  /** get the last items in the column. Typically for fetching the single last item in the column
    * to report the range of keys.  */
  override def lastKey()
      ( implicit execution: ExecutionContext, parentSpan: Span ) : Future[Option[T]] = {
    readOneKey("lastKey", ReadLastKey.apply)
  }

  /** get the last items in the column. Typically for fetching the single last item in the column
    * to report the range of keys.  */
  override def firstKey()
      ( implicit execution: ExecutionContext, parentSpan: Span ) : Future[Option[T]] = {
    readOneKey("firstKey", ReadFirstKey.apply)
  }

  override def countItems(optStart: Option[T] = None, optEnd: Option[T] = None)
      ( implicit execution: ExecutionContext, parentSpan: Span ): Future[Long] = {
    import nest.sparkle.store.cassandra.ObservableResultSetA._
    val span = Span.start("countItems", parentSpan)

    val statement =
      (optStart, optEnd) match {
        case (None,None) =>
          prepared.statement(CountAll(tableName)).bind(
            Seq[AnyRef](dataSetName, columnName, rowIndex): _*)
        case (Some(start:AnyRef), Some(end:AnyRef)) =>
          prepared.statement(CountRange(tableName)).bind(
            Seq[AnyRef](dataSetName, columnName, rowIndex, start, end): _*)
        case (Some(start:AnyRef), None) =>
          prepared.statement(CountFromStart(tableName)).bind(
            Seq[AnyRef](dataSetName, columnName, rowIndex, start): _*)
        case x =>
          NYI(s"countItems range variant $x")
       }

    val obsRows = prepared.session.executeAsync(statement).observerableRowsA()(execution, span)
    obsRows.toFutureSeq.map { rows:Seq[Seq[Row]] =>
      val optRow = rows.headOption.flatMap(_.headOption)
      val result = optRow.map(_.getLong(0)).getOrElse(0L)
      result
    }
  }

  private def readOneKey(spanName:String, makeTableOperation: String => TableOperation)
      ( implicit execution: ExecutionContext, parentSpan: Span ) : Future[Option[T]] = {
    import nest.sparkle.store.cassandra.ObservableResultSetA._
    val span = Span.start(spanName, parentSpan)

    val statement = prepared.statement(makeTableOperation(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex): _*
    )
    val obsRows = prepared.session.executeAsync(statement).observerableRowsA()(execution, span)
    obsRows.toFutureSeq.map { rows:Seq[Seq[Row]] =>
      val optRow = rows.headOption.flatMap(_.headOption)
      optRow map { row => keySerializer.fromRow(row, 0)}
    }
  }


  /** read all the column values from the column */
  private def readAll()  // format: OFF
    ( implicit executionContext: ExecutionContext, parentSpan:Span )
    : OngoingData[T, U] = { // format: ON
    log.trace(s"readAll from $tableName $columnPath")
    val readStatement = prepared.statement(ReadAll(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex): _*)

    OngoingData(initial = readDataRows(readStatement), ongoing = ongoingRead())
  }

  private def readFromStart(start: T) // format: OFF
      (implicit execution:ExecutionContext, parentSpan:Span): OngoingData[T,U] = { // format: ON
    OngoingData(readFromStartCurrent(start), ongoingRead())
  }

  /** read the data at or after a given start key,
    * returning an Observable that completes with a single read of row data
    * from C*
    */
  private def readFromStartCurrent(start: T) // format: OFF
      ( implicit execution:ExecutionContext, parentSpan: Span): Observable[DataArray[T,U]] = { // format: ON
    log.trace(s"readFromStartCurrent from $tableName $columnPath $start")
    val readStatement = prepared.statement(ReadFromStart(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex,
        start.asInstanceOf[AnyRef]): _*)
    readDataRows(readStatement)
  }

  private def handleColumnUpdate(columnUpdate: ColumnUpdate[T]) // format: OFF
      ( implicit execution:ExecutionContext, parentSpan: Span): Observable[DataArray[T,U]] = { // format: ON
    log.trace(s"handleColumnUpdate received $columnUpdate")
    readFromStartCurrent(columnUpdate.start)
  }

  /** listen for writes, and trigger a read each time new data is available.
    * Return an observable that never completes (except if there's an error).  */
  // TODO the read should only triggered if the caller subscribes to the returned observable.
  private def ongoingRead()
      ( implicit executionContext: ExecutionContext, parentSpan:Span): Observable[DataArray[T, U]] = {
    writeListener.listen(columnPath).flatMap { written:WriteEvent =>
      written match { 
        case columnUpdate:ColumnUpdate[T] => handleColumnUpdate(columnUpdate)
        case _                            => Observable.empty
      }
    }
  }

  private def readBoundedRange(start: T, end: T) // format: OFF
      ( implicit execution:ExecutionContext, parentSpan: Span): OngoingData[T, U] = { // format: ON
    log.trace(s"readBoundedRange from $tableName $columnPath $start $end")
    val readStatement = prepared.statement(ReadRange(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex,
        start.asInstanceOf[AnyRef], end.asInstanceOf[AnyRef]): _*)

    OngoingData(readDataRows(readStatement), Observable.empty)
  }


  private def readDataRows( statement: BoundStatement ) // format: OFF
      ( implicit execution:ExecutionContext, parentSpan: Span ): Observable[DataArray[T, U]] = { // format: ON
    import nest.sparkle.store.cassandra.ObservableResultSetA._

    val span = Span.start("readDataRows", parentSpan)
    val rows = prepared.session.executeAsync(statement).observerableRowsA()(execution, span)
    rows map rowsDecoder
  }

  private def rowsDecoder(rows: Seq[Row]): DataArray[T, U] = {
    log.trace(s"rowsDecoder: $rows")
    val size = rows.size
    val arguments = new Array[T](size)
    val values    = new Array[U](size)
    (0 until size) foreach { index =>
      val row = rows(index)
      arguments(index) = keySerializer.fromRow(row, 0)
      values(index) = valueSerializer.fromRow(row, 1)
    }
    DataArray(arguments, values)
  }


  //
  // old Event-based code path below, to be removed
  //

  /** read a slice of events from the column */      // format: OFF
  override def readRangeOld(start:Option[T] = None, end:Option[T] = None, limit:Option[Long] = None, parentSpan:Option[Span])
                           (implicit execution: ExecutionContext): OngoingEvents[T,U] = { // format: ON
  implicit val span = parentSpan.getOrElse(DummySpan)
    (start, end) match {
      case (None, None)             => readAllOld()
      case (Some(start), Some(end)) => readBoundedRangeOld(start, end)
      case (Some(start), None)      => readFromStartOld(start)
      case _                        => ???
    }
  }

  /** read all the column values from the column */
  private def readAllOld() // format: OFF
                        ( implicit executionContext: ExecutionContext, parentSpan:Span): OngoingEvents[T, U] = { // format: ON
    log.trace(s"readAllOld from $tableName $columnPath")
    val baseStatement = prepared.statement(ReadAll(tableName))
    val args =  Seq[AnyRef](dataSetName, columnName, rowIndex)
    val readStatement = baseStatement.bind(args: _*)

    OngoingEvents(initial = readEventRowsOld(readStatement), ongoing = ongoingReadOld())
  }

  private def rowDecoder(row: Row): Event[T, U] = {
    log.trace(s"rowDecoder: $row")
    val argument = keySerializer.fromRow(row, 0)
    val value = valueSerializer.fromRow(row, 1)
    Event(argument.asInstanceOf[T], value.asInstanceOf[U])
  }


  private def readFromStartOld(start: T) // format: OFF
                              ( implicit execution:ExecutionContext, parentSpan:Span): OngoingEvents[T,U] = { // format: ON
    OngoingEvents(readFromStartCurrentOld(start), ongoingReadOld())
  }

  /** listen for writes, and trigger a read each time new data is available.
    * Return an observable that never completes (except if there's an error). */
  // TODO the read should only triggered if the caller subscribes to the returned observable.
  private def ongoingReadOld()
                            ( implicit executionContext: ExecutionContext, parentSpan:Span): Observable[Event[T, U]] = {
    writeListener.listen(columnPath).flatMap { written:WriteEvent  =>
      written match {
        case columnUpdate:ColumnUpdate[T] => handleColumnUpdateOld(columnUpdate)
        case _                            => Observable.empty
      }
    }
  }

  private def readBoundedRangeOld(start: T, end: T) // format: OFF
                                 ( implicit execution:ExecutionContext, parentSpan: Span ): OngoingEvents[T, U] = { // format: ON
    log.trace(s"readBoundedRangeOld from $tableName $columnPath $start $end")
    val readStatement = prepared.statement(ReadRange(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex,
        start.asInstanceOf[AnyRef], end.asInstanceOf[AnyRef]): _*)

    OngoingEvents(readEventRowsOld(readStatement), Observable.empty)
  }

  /** read the data at or after a given start key,
    * returning an Observable that completes with a single read of row data
    * from C*
    */
  private def readFromStartCurrentOld(start: T) // format: OFF
                                     (implicit execution:ExecutionContext, parentSpan: Span): Observable[Event[T,U]] = { // format: ON
    log.trace(s"readFromStartCurrentOld from $tableName $columnPath $start")
    val readStatement = prepared.statement(ReadFromStart(tableName)).bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex,
        start.asInstanceOf[AnyRef]): _*)
    readEventRowsOld(readStatement)
  }
  private def handleColumnUpdateOld(columnUpdate: ColumnUpdate[T]) // format: OFF
                                   ( implicit execution:ExecutionContext, parentSpan: Span): Observable[Event[T,U]] = { // format: ON
    log.trace(s"handleColumnUpdateOld received $columnUpdate")
    readFromStartCurrentOld(columnUpdate.start)
  }

  private def readEventRowsOld(statement: BoundStatement) // format: OFF
                              (implicit execution:ExecutionContext, parentSpan: Span): Observable[Event[T, U]] = { // format: ON
    import nest.sparkle.store.cassandra.ObservableResultSet._

    val span = Span.start("readEventRowsOld", parentSpan)
    val rows = prepared.session.executeAsync(statement).observerableRows(Option(span))
    rows map rowDecoder
  }

}
