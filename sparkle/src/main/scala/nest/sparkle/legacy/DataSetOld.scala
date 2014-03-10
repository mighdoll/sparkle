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
import scala.concurrent.Future
import nest.sparkle.util.Opt.option2opt


/** A time series data set, containing timestamps and and arbitrary number of data columns (with
  * double formatted values)
  */
trait DataSetOld {
  /** name of the data set, .e.g. "2013-04-01T10:10:00Z-test-run" */
  def name(): String

  /** Return a slice from a single data column */
  def data(metricName: String, start: Opt[DateTime] = None, end: Opt[DateTime] = None,
           max: Opt[Int] = None, edgeExtra: Opt[Boolean] = None, 
           summarize: Opt[String] = None, filter: Opt[Array[Double]] = None): Future[Array[Datum]]

  def info(): Future[DataSetInfo]
  def metricInfo(name: String): Future[MetricInfo] 
  def minValue(metricName: String): Future[Double]  
  def maxValue(metricName: String): Future[Double]
  def uniqueValues(metricName:String):Future[Array[Double]]
  def minDomain: Future[Long]
  def maxDomain: Future[Long]
}

