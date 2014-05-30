package nest.sg

import nest.sparkle.util.Log
import nest.sparkle.store.cassandra.CassandraStore
import scala.concurrent.duration._
import nest.sparkle.util.FutureToTry._
import nest.sparkle.store.Event
import nest.sparkle.util.ObservableFuture._
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import rx.lang.scala.Observable
import scala.concurrent.ExecutionContext
import nest.sparkle.store.Store

class StorageConsole(store:Store, execution:ExecutionContext) extends Log {
  case class ColumnEvents(name: String, events: Seq[Event[Long, Any]])
  implicit def _execution = execution

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
            val seqFutureColumns = names.map { name => store.column[Long, Any](name) }
            Future.sequence(seqFutureColumns)
          }

          val futureEvents = futureColumns.flatMap { columns =>
            val seqFutureEvents = columns.map { column =>
              column.readRange(None, None).toFutureSeq
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
  def eventsByColumnPath(columnPath: String): Seq[Event[Long, Any]] = {
    val futureEvents =
      for {
        column <- store.column[Long, Any](columnPath)
        events <- column.readRange(None, None).toFutureSeq
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
      case cassandraStore: CassandraStore =>
        cassandraStore.columnCatalog.allColumns()
    }
  }
  
}

