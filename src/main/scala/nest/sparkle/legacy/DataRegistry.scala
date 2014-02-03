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

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.io.FileNotFoundException
import nest.sparkle.util.OptionConversion._
import spray.http.DateTime
import nest.sparkle.util.Opt._

/** Concrete DataRegistry implementations need to implement these */
trait DataRegistryProvider {
  /** Return the names of all data sets currently known.  (Note that the set of names may change
    * while the server is running, so this routine may return different values over time.)
    */
  def allSets(): Future[Iterable[String]]

  /** return a DataSet if this registry can find it.   */
  protected def findDataSet(name: String): Future[DataSetOld]

  protected implicit def execution: ExecutionContext
}

/** Asynchronous API to return numeric data and dataset metadata.  Data is presented in
  * a hierarchy, named DataSets.  Each DataSet contains 'metrics'.   Each metric is queryable
  * by time range, and optionally summarized (e.g. to calculate an average and return fewer points).
  * The API is used by the REST layer to serve up points and metadata for graphical display.
  */
trait DataRegistry extends DataRegistryProvider {

  /** return metadata about a dataset (e.g. list of metrics, domain) */
  def dataSetInfo(name: String): Future[DataSetInfo] = {
    findDataSet(name).flatMap{ _.info() }
  }

  /** return metadata about a particular metric in a dataset (e.g. domain and range) */
  def metricInfo(dataSetName: String, metricName: String): Future[MetricInfo] = {
    for {
      dataSet <- findDataSet(dataSetName)
      metric <- dataSet.metricInfo(metricName)
    } yield metric
  }

  /** return metadata about a particular metric in a dataset (e.g. domain and range) */
  def metricInfoWithUniques(dataSetName: String, metricName: String): Future[MetricInfoWithUniques] = {
    for {
      dataSet <- findDataSet(dataSetName)
      metric <- metricInfo(dataSetName, metricName)
      uniques <- dataSet.uniqueValues(metricName)
    } yield {
      MetricInfoWithUniques(domain = metric.domain, range = metric.range, units = metric.units,
        uniqueValues = uniques)
    }
  }

  /** return an array of time series data points for a given data set and time range */
  def data(dataSetName: String, metricName: String, start: Option[Long], end: Option[Long], max: Option[Int],
           edge: Option[Boolean], summarize: Option[String], filter: Option[Array[Double]]): Future[Array[(Long, Double)]] = {
    for {
      dataSet <- findDataSet(dataSetName)
      startDateOpt = start.map(DateTime(_))
      endDateOpt = end.map(DateTime(_))
      datums <- dataSet.data(metricName, startDateOpt, endDateOpt, max, edge, summarize, filter)
    } yield {
      datums map { datum => (datum.time, datum.value) }
    }
  }

  def uniqueValues(dataSetName: String, metricName: String): Future[Array[Double]] = {
    for {
      dataSet <- findDataSet(dataSetName)
      uniques <- dataSet.uniqueValues(metricName)
    } yield {
      uniques
    }
  }

  private def notFoundException(dataSetName: String, metricName: String): FileNotFoundException = {
    new FileNotFoundException(s"metric $metricName not found in set $dataSetName")
  }
}

