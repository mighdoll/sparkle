package nest.sg

import java.text.NumberFormat
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.util.{Failure, Success}

import rx.lang.scala.Observable

import nest.sparkle.datastream.DataArray
import nest.sparkle.measure.DummySpan
import nest.sparkle.store.{WriteableStore, Event, Store}
import nest.sparkle.store.cassandra.CassandraStoreReader
import nest.sparkle.util.FutureToTry._
import nest.sparkle.util.Log
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.util.ReflectionUtil.caseClassFields
import nest.sparkle.util.FutureAwait.Implicits._

case class ColumnEvents(name: String, events: Seq[Event[Long, Double]])

/** a console for making queries to the store from the scala console */
trait StorageConsole extends Log {

  def store:Store
  implicit def executionContext:ExecutionContext

  /** Return events for all columns inside a dataset.
    * Only direct children a returned (it does not recurse on nested dataSets).
    */
  def eventsByDataSet(dataSet: String): Seq[ColumnEvents] = {
    val tryDataSet = store.dataSet(dataSet).toTry
    val tryColumnEvents =
      tryDataSet.flatMap{ dataSet =>
        val futureNames = dataSet.childColumns.toFutureSeq

        val futureColumnEvents: Future[Seq[ColumnEvents]] = futureNames.flatMap { names =>
          val futureColumns = {
            val seqFutureColumns = names.map { name => store.column[Long, Double](name) }
            Future.sequence(seqFutureColumns)
          }

          val futureEvents = futureColumns.flatMap { columns =>
            val seqFutureEvents = columns.map { column =>
              column.readRange(None, None).initial.toFutureSeq
            }

            Future.sequence(seqFutureEvents)
          }

          futureEvents.map { eventsSeq =>
            eventsSeq.zip(names).map { case (events, name) => ColumnEvents(name, events) }
          }
        }
        futureColumnEvents.toTry
      }

    tryColumnEvents match {
      case Success(seqColumnEvents) =>
        seqColumnEvents
      case Failure(err) =>
        log.error("column loading failed", err)
        Seq()
    }
  }

  /** Return events from a given column path */
  def eventsByColumnPath(columnPath: String): Seq[Event[Long, Double]] = {
    val futureEvents =
      for {
        column <- store.column[Long, Double](columnPath)
        events <- column.readRange(None, None).initial.toFutureSeq
      } yield {
        events
      }

    futureEvents.toTry match {
      case Success(seqColumnEvents) =>
        seqColumnEvents
      case Failure(err) =>
        log.error("column loading failed", err)
        Seq()
    }
  }

  /** return an observable of _all_ columns in the store */
  def allColumns(): Observable[String] = {
    store match {
      case cassandraStore: CassandraStoreReader =>
        cassandraStore.columnCatalog.allColumns()
    }
  }

  /** return the data in a column as a single DataArray */
  def columnData[T: ClassTag](columnPath:String):DataArray[Long,T] = {
    futureColumnData[T](columnPath).toTry match {
      case Success(data) => data
      case Failure(err)  =>
        log.error("column loading failed", err)
        DataArray.empty[Long, T]
    }
  }

  private def futureColumnData[T:ClassTag](columnPath:String):Future[DataArray[Long,T]] = {
    for {
      column <- store.column[Long, T](columnPath)
      dataSeq <- column.readRangeA(parentSpan = Some(DummySpan)).initial.toFutureSeq
    } yield {
      dataSeq.reduceLeft(_ ++ _)
    }
  }

// this is hard to do generically, Shapeless would help after we upgrade.
//  def records[T: TypeTag](prefix:String):Seq[T] = {
//    val futureColumns = for {
//      fieldName <- caseClassFields[T]
//      if (fieldName != "time")
//      columnPath = s"$prefix/$fieldName"
//    } yield {
//      futureColumnData[Any](columnPath)
//    }
//    val columns:Seq[DataArray[Long, Any]] = Future.sequence(futureColumns).await
//    columns.map(_.values)
////    val x = columns.map(_.values).zip
//
//    ???
//  }


}

