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

object SampleData extends RamDataSet {
  val name = "sample"

  val timeStrings = List("2013-01-19T22:13:10", "2013-01-19T22:13:20", "2013-01-19T22:13:30",
    "2013-01-19T22:13:40", "2013-01-19T22:13:50", "2013-01-19T22:14:00", "2013-01-19T22:14:10")
  val time = timeStrings.map { DateTime.fromIsoDateTimeString(_).get.clicks } toArray
  val p90 = RamDataColumn("p90", Array(25, 26, 31, 32, 28, 25, 20))
  val dataColumns = List(p90)
}

object SampleDataUneven extends RamDataSet {
  val name = "sample"

  val timeStrings = List("2013-01-19T22:13:10", "2013-01-19T22:13:11", "2013-01-19T22:13:12",
    "2013-01-19T22:13:40", "2013-01-19T22:13:50", "2013-01-19T22:14:00", "2013-01-19T22:14:10")
  val time = timeStrings.map { DateTime.fromIsoDateTimeString(_).get.clicks } toArray
  val p90 = RamDataColumn("p90", Array(25, 26, 31, 32, 28, 25, 20))
  val dataColumns = List(p90)
}
