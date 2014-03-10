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

case class SparseReaderStatements(val readAll: PreparedStatement)

/** read only access to a cassandra source event column */
case class SparseColumnReader[T, U] (dataSetName: String, columnName: String, session: Session, 
      catalog: ColumnCatalog) 
    extends Column[T, U] with PreparedStatements[SparseReaderStatements] with ColumnSupport {
  def name = columnName  
  
  private val domainSerializer = serializers.LongSerializer // TODO read these from catalog
  private val rangeSerializer = serializers.DoubleSerializer
  def keyType = typeTag[Long]
  def valueType = typeTag[Double]

  /** return a successful future if the column exists */
  def exists(implicit context: ExecutionContext): Future[Unit] = {
    catalog.tableForColumn(columnPath).map{ _ => () }
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

  /** read all the column values from the column */
  private def readAll()(implicit executionContext: ExecutionContext): Observable[Event[T, U]] = { // format: ON
    val statement = readerStatements.readAll.bind(
      Seq[AnyRef](dataSetName, columnName, rowIndex): _*)

    def rowDecoder(row: Row): Event[T, U] = {
      val argument = domainSerializer.fromRow(row, 0)
      val value = rangeSerializer.fromRow(row, 1)
      Event(argument.asInstanceOf[T], value.asInstanceOf[U])    
    }

    val rows = session.executeAsync(statement).observerableRows
    rows map rowDecoder
  }

  private def makeStatements(): SparseReaderStatements = {
    SparseReaderStatements(
      readAll = session.prepare(readAllStatement)
    )
  }

  private val readAllStatement = """
    SELECT argument, value FROM bigint0double
    WHERE dataSet = ? AND column = ? AND rowIndex = ? 
    """
    
  private def readerStatements(): SparseReaderStatements = {
    preparedStatements(makeStatements)
  }

}
