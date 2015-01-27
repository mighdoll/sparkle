package nest.sg

import scala.reflect.ClassTag

import nest.sparkle.datastream.DataArray
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
import nest.sparkle.store.cassandra.CassandraStoreReader

/** a console for making queries to the store from the scala console */
object StorageConsole extends ConsoleServer with Log {
  lazy val storage = new ConcreteStorageConsole(server.store, server.actorSystem.dispatcher)
}


case class ColumnEvents(name: String, events: Seq[Event[Long, Double]])

/** a console for making queries to the store from the scala console */
class ConcreteStorageConsole(store:Store, execution:ExecutionContext) extends Log {
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

  def columnData[T: ClassTag](columnPath:String):DataArray[Long,T] = {
    val futureResult =
      for {
        column <- store.column[Long,T](columnPath)
        data <- column.readRangeA().initial.toFutureSeq
      } yield data

    futureResult.toTry match {
      case Success(dataSeq) =>
        dataSeq.reduce(_ ++ _)
      case Failure(err) =>
        log.error("column loading failed", err)
        DataArray.empty[Long, T]
    }
  }

  case class Measure(time:Long, name:String, traceId:String, duration:Long)
  def measurementsData(measurementName:String):DataArray[Long,Long] = {
    val durations = columnData[Long]("sparkle-measurements/duration")
    val traceIds = columnData[String]("sparkle-measurements/traceId")
    val names = columnData[String]("sparkle-measurements/name")


    val validTimes = traceIds.collect{case (k,v) if v == measurementName => k}

    ???
  }

}

