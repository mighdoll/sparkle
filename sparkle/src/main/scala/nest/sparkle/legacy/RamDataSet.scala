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

package nest.sparkle.legacy

import spray.http.DateTime
import nest.sparkle.util.Opt
import scala.collection.mutable
import scala.concurrent.Future
import java.io.FileNotFoundException
import nest.sparkle.util.OptionConversion._
import nest.sparkle.util.Opt._
import scala.concurrent.ExecutionContext.Implicits.global

case class RamDataColumn(name: String, data: Array[Double])
case class StringColumn(name: String, data: Array[String])

case class ColumnNotFoundException(message: String) extends RuntimeException(message)
/** A RAM resident DataSet containing one time column (the domain), a set of named
  * numeric data columns, and a set of named string columns.  All columns have the same
  * number of rows.  Queries by column name and time range are supported via the 'data' method.
  */
trait RamDataSet extends DataSetOld {
  def time: Array[Long]
  def dataColumns: Iterable[RamDataColumn]

  /** return an array of time-stamped data extracted from specified data column */
  def data(metricName: String, start: Opt[DateTime], end: Opt[DateTime],
           max: Opt[Int], edgeExtra: Opt[Boolean], summarize: Opt[String],
           valueFilter: Opt[Array[Double]]): Future[Array[Datum]] = {

    val approxMaxData = max.getOrElse(10000)
    if (timeColumn(metricName)) {
      timeData(start, end, approxMaxData, summarize)
    } else {
      valueData(metricName, start, end, approxMaxData, edgeExtra, summarize, valueFilter)
    }
  }

  /** return an array of time-stamped data extracted from specified data column */
  private def valueData(metricName: String, start: Option[DateTime], end: Option[DateTime],
                        max: Int, edgeExtra: Option[Boolean], summarize: Option[String],
                        valueFilter: Option[Array[Double]]): Future[Array[Datum]] = {

    findColumn(metricName) map { column =>
      val (startDex, afterEnd) = timeRange(start, end)
      val summaryFn = Summarize(summarize)
      val raw = datumSlice(column, startDex, afterEnd)
      val filtered = valueFilter match {
        case Some(filterArray) =>
          raw.filter { datum => filterArray.contains(datum.value) }
        case None => raw
      }
      val main = summaryFn(filtered, max)

      val extra = edgeExtra.getOrElse(false)
      val result =
        if (extra) {
          val extraDexBefore = if (startDex > 0) Some(startDex - 1) else None
          val extraDexAfter = if (afterEnd < time.length) Some(afterEnd + 1) else None

          val extraBefore = extraDexBefore.toArray flatMap { i => datumSlice(column, i, i + 1) }
          val extraAfter = extraDexAfter.toArray flatMap { i => datumSlice(column, i, i + 1) }

          extraBefore ++ main ++ extraAfter
        } else {
          main
        }
      Future.successful(result)
    } getOrElse {
      Future.failed(new FileNotFoundException(s"column $metricName not found"))
    }
  }

  /** return data from the time column */
  private def timeData(start: Option[DateTime], end: Option[DateTime],
                       max: Int, summarize: Option[String]): Future[Array[Datum]] = {
    val (startDex, afterEnd) = timeRange(start, end)
    val summaryFn = Summarize(summarize)
    val raw = timeSlice(startDex, afterEnd)
    val converted = raw map { timeDatum => Datum(timeDatum.time, timeDatum.value.toDouble) } // (we don't actually use the value)
    val results = summaryFn(converted, max)
    Future.successful(results)
  }

  /** return a slice of timestamped data from one column */
  def datumSlice(column: RamDataColumn, startDex: Int, afterEnd: Int): Array[Datum] = {
    time.slice(startDex, afterEnd) zip column.data.slice(startDex, afterEnd) map {
      case (t, data) => Datum(t, data)
    }
  }

  /** return a slice of just the times */
  def timeSlice(startDex: Int, afterEnd: Int): Array[TypedDatum[Long]] = {
    time.slice(startDex, afterEnd) map { t => TypedDatum(t, t) }
  }

  /** return min value in the column, or None if the column is empty or missing */
  def minValueOpt(metricName: String): Option[Double] = {
    findColumn(metricName).flatMap { column =>
      column.data.reduceOption { (a, b) => if (a <= b) a else b }
    }
  }

  /** return a future that completes with the min value in the column */
  def minValue(metricName: String): Future[Double] =
    minValueOpt(metricName).futureOrFailed(notFound(metricName))

  /** return max value in the column, or None if the column is empty or missing */
  def maxValueOpt(metricName: String): Option[Double] = {
    findColumn(metricName).flatMap { column =>
      column.data.reduceOption { (a, b) => if (a >= b) a else b }
    }
  }

  /** return a future that completes with the max value in the column */
  def maxValue(metricName: String): Future[Double] =
    maxValueOpt(metricName).futureOrFailed(notFound(metricName))

  private def notFound(column: String) = ColumnNotFoundException(column)
  private def emptyFailure = new RuntimeException("empty data set")

  /** minimum time */
  def minDomain: Future[Long] = time.headOption.futureOrFailed(emptyFailure)

  /** max time */
  def maxDomain: Future[Long] = time.lastOption.futureOrFailed(emptyFailure)

  /** return an array of the unique values in the data set */
  def uniqueValuesSynchronous(metricName: String): Array[Double] = {
    val optData = findColumn(metricName).map{ _.data } // TODO should return option

    optData.toArray.flatMap { data =>
      val values = mutable.HashSet[Double]()
      data.foreach { value =>
        values += value
      }
      val array = values.toArray
      array
    }
  }

  /** return an array of the unique values in the data set */
  def uniqueValues(metricName: String): Future[Array[Double]] = {
    Future.successful(uniqueValuesSynchronous(metricName))
  }

  /** return the indices in the time array for the specified time bounds */
  private def timeRange(start: Option[DateTime], end: Option[DateTime]): (Int, Int) = {
    val startDex: Int = {
      start map { startTime =>
        time.indexWhere(_ >= startTime.clicks)
      } getOrElse 0
    }
    val afterEnd: Int = {
      end map { endTime =>
        time.length - time.reverseIterator.indexWhere(_ <= endTime.clicks)
      } getOrElse time.length
    }
    (startDex, afterEnd)
  }

  /** return a future that completes with a structure describing the dataset stored in this `RamDataSet` */
  def info(): Future[DataSetInfo] = {
    infoOption.toFutureOr(new RuntimeException("info failed"))
  }

  /** return a structure describing the dataset stored in this `RamDataSet` */
  def infoOption(): Option[DataSetInfo] = {
    val metrics = dataColumns map { _.name }

    val domain = tupleOptInvert(time.headOption, time.lastOption)
    Some(DataSetInfo(metrics, domain))
  }

  /** convert a pair of options to an option of a pair */
  def tupleOptInvert[A, B](tuple: Tuple2[Option[A], Option[B]]): Option[Tuple2[A, B]] = {
    tuple match {
      case (Some(a), Some(b)) => Some(a, b)
      case _                  => None
    }
  }

  /** return a future that completes with structure describing a particular column */
  def metricInfo(metricName: String): Future[MetricInfo] = {
    if (timeColumn(metricName)) {
      timeMetricInfo()
    } else {
      metricInfoDataOption(metricName).futureOrFailed(notFound(metricName))
    }
  }

  /** return a MetricInfo for the time column*/
  private def timeMetricInfo():Future[MetricInfo] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    info() map { setInfo =>
      val range = setInfo.domain.map {
        case (start,end) => (start.toDouble, end.toDouble)
      }
      MetricInfo(domain = setInfo.domain, range = range)
    }
  }

  private def timeColumn(metricName:String):Boolean =
    metricName == "time"

  /** return a structure describing a particular column */
  def metricInfoDataOption(metricName: String): Option[MetricInfo] = {
    findColumn(metricName).flatMap { column =>
      val min = column.data.headOption map { _ => column.data.min }
      val max = column.data.headOption map { _ => column.data.max }
      val range = tupleOptInvert((min, max))
      infoOption().map { setInfo =>
        MetricInfo(domain = setInfo.domain, range = range)
      }
    }
  }

  /** optionally return the named data column */ // TODO make a Try
  private def findColumn(metricName: String): Option[RamDataColumn] =
    dataColumns.find(column => column.name == metricName)

}
