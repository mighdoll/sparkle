package nest.sparkle.store.cassandra

import scala.collection.JavaConverters._

import scala.concurrent.{Future, ExecutionContext}

import com.datastax.driver.core.{BatchStatement, Session}

import nest.sparkle.store.Store
import nest.sparkle.util.{Instrumented, Log}
import nest.sparkle.util.GuavaConverters._

/**
 * Object to write rows to a column table.
 */ 
case class TableWriter(
  session: Session,
  tableName: String,
  writeNotifier: WriteNotifier
) extends Instrumented
  with Log
{
  
  protected val batchMetric = metrics.timer(s"store-$tableName-writes")
  protected val batchSizeMetric = metrics.meter(s"store-$tableName-batch-size")
  
  protected lazy val insertPrepared = session.prepare(insertStatement())
  
  private def insertStatement() = s"""
      INSERT INTO $tableName
      (dataSet, column, rowIndex, argument, value)
      VALUES (?, ?, ?, ?, ?)
      """

  /** write a bunch of column data in a batch */ // format: OFF
  def write(rows: Iterable[ColumnRowData])
      (implicit executionContext:ExecutionContext): Future[Unit] = 
  { // format: ON
     val written = writeInBatches(rows)
    
    // Send write notifications on success
    written.map { _ =>
      // Group rows by columnPath to find start & end
      val columnPaths = rows.groupBy(_.columnPath)
      columnPaths.foreach {
        case (columnPath, list) =>
          val keys = list.map(_.key)
          val min = keys.head  // assume rows are still sorted after groupBy
          val max = keys.last
          writeNotifier.notify(columnPath, ColumnUpdate(min, max))
      }
    }
  }

  private val batchSize = 25000 // CQL driver has a max batch size of 64K
  
  /** write a bunch of column values in a batch */ // format: OFF
  private def writeInBatches(items: Iterable[ColumnRowData])
      (implicit executionContext:ExecutionContext): Future[Unit] = 
  { // format: ON
    val batches = items.grouped(batchSize).map { rowBatch =>
      val insertGroup = rowBatch.map { row =>
        val (dataSetName, columnName) = Store.setAndColumn(row.columnPath)
        insertPrepared.bind(
          Seq[AnyRef](dataSetName, columnName, TableWriter.rowIndex, row.key, row.value): _*
        )
      }

      val batch = new BatchStatement()
      val statements = insertGroup.toSeq.asJava
      batchSizeMetric.mark(statements.size)
      batch.addAll(statements)
      batch
    }

    val resultsIterator =
      batches.map { batch =>
        val timer = batchMetric.timerContext()
        val result = session.executeAsync(batch).toFuture
        result.onComplete(_ => timer.stop())
        val resultUnit = result.map { _ => }  // release ResultSet
        resultUnit
      }
    
    Future.sequence(resultsIterator).map { _ => }
  }
}

object TableWriter {
  
  val rowIndex = 0.asInstanceOf[AnyRef]  // hard-coded for now
  
}
