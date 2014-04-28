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

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext
import spray.json._
import spray.httpx.SprayJsonSupport._
import spray.routing.Directives
import nest.sparkle.legacy.DataServiceJson._
import nest.sparkle.time.server.DataService
import nest.sparkle.time.server.RichComplete
import spray.routing.Directives

/** legacy protocol, this will go away */
trait DataServiceV0 extends RichComplete with Directives {
  self:DataService =>

  implicit def executionContext: ExecutionContext

  lazy val v0protocol = {
    dataRequest ~
    metricInfoRequest ~
    dataSetInfoRequest ~
    allSetInfoRequest
  }

  private val dataRequest = { // return time series data points
    // format: OFF
    (path("data" / Segment / Rest) & parameters('start.as[Long] ?,
                                                   'end.as[Long] ?,
                                                   'max.as[Double] ?,
                                                   'edge.as[Boolean] ?,
                                                   'summary.as[String] ?,
                                                   'filter.as[String] ?
                                                )
     ) { (metricName, dataSet, start, end, max, edge, summary, filter) =>
       val filterList = filter.map { filterParam =>
         filterParam.split(",").map {stringFilter => stringFilter.toDouble}
       }
       val maxAsInt = max.map(_.toInt)
       richComplete(registry.data(dataSet, metricName, start, end, maxAsInt, edge, summary, filterList))
     }
    // format: ON
  }

  private val metricInfoRequest = // return meta data about this data set and metric
    (path("column" / Segment / Rest) & parameters('uniques.as[Boolean] ?)) { (metricName, dataSet, uniques) =>
      uniques match {
        case Some(true) =>
          richComplete(registry.metricInfoWithUniques(dataSet, metricName))
        case _ =>
          richComplete(registry.metricInfo(dataSet, metricName))
      }
    }

  private val dataSetInfoRequest = // return meta data about this data set
    path("info" / Rest) { dataSet =>
      if (dataSet.isEmpty()) reject else richComplete(registry.dataSetInfo(dataSet))
    }

  private val allSetInfoRequest = // return meta data about all the sets
    path("info") {
      dynamic {
        richComplete(registry.allSets())
      }
    }


}
