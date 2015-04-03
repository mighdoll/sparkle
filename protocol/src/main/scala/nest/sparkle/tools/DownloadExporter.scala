package nest.sparkle.tools

import nest.sparkle.store.Store
import com.typesafe.config.Config
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import nest.sparkle.util.ObservableFuture._

/** An Exporter that exports data from the store into a string suitable for sending over an http response */
case class DownloadExporter(rootConfig:Config, store:Store) // format: OFF 
    (implicit val execution:ExecutionContext) extends TsvExporter { // format: ON

  /** export a leaf dataSet, where a leaf dataSet is a dataSet with only
    * columns (not other dataSets) as children */
  def exportLeafDataSet(dataSet:String):Future[String] = {
    leafDatSet(dataSet).flatMap { tabular =>
      tsvLines(tabular).toFutureSeq.map { seq =>
        seq.mkString("")
      }
    }
  }

  /** export a column */
  def exportColumn(columnPath:String):Future[String] = {
    column(columnPath).flatMap { tabular =>
      tsvLines(tabular).toFutureSeq.map { seq =>
        seq.mkString("")
      }
    }
  }
  
}
