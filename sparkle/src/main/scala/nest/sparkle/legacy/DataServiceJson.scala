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

import spray.json.DefaultJsonProtocol

// data sent to the browser

/** info about one particular metric (e.g. one data column) */
case class MetricInfo(domain:Option[Tuple2[Long,Long]], range:Option[Tuple2[Double, Double]], units:Option[String] = None)

/** info about one particular metric (e.g. one data column), including all the unique values in the column */
case class MetricInfoWithUniques(domain:Option[Tuple2[Long,Long]],
    range:Option[Tuple2[Double, Double]],
    uniqueValues:Array[Double],
    units:Option[String] = None)

/** list of data columns available in a data set (and the aggregate time range for each) */
case class DataSetInfo(metrics:Iterable[String], domain:Option[Tuple2[Long, Long]])

/** spray json converters for objects we send to the browser */
object DataServiceJson extends DefaultJsonProtocol {
  implicit val DataSetFormat = jsonFormat2(DataSetInfo)
  implicit val MetricInfoFormat = jsonFormat3(MetricInfo)
  implicit val MetricInfoLongFormat = jsonFormat4(MetricInfoWithUniques)
  implicit val DatumFormat = jsonFormat2(Datum.apply)
}
