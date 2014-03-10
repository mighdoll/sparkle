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

import org.scalatest.FunSuite
import org.scalatest.Matchers
import spray.http.DateTime
import spray.util._
import nest.sparkle.util.Opt._

class TestDataSet extends FunSuite with Matchers {
  test("all by time") {
    val data = SampleData.data("p90",
      start = DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get,
      end = DateTime.fromIsoDateTimeString("2013-01-19T22:14:10").get).await
    data.length should be(7)
  }

  test("all by max") {
    val data = SampleData.data("p90",
      start = DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get).await
    data.length should be(7)
  }

  test("some by max") {
    val data = SampleData.data("p90",
      start = DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get,
      max = 2).await
    data.length should be(2)
  }

  test("extra edge") {
    def requestTwo(edgeExtra:Boolean):Array[Datum] = {
      SampleData.data("p90",
      start = DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get,
      end = DateTime.fromIsoDateTimeString("2013-01-19T22:13:20").get,
      edgeExtra = edgeExtra).await
    }
    
    val data = requestTwo(edgeExtra=false)
    data.length should be(2)
    
    val data3 = requestTwo(edgeExtra=true)
    data3.length should be(3)
  }

  test("all no params") {
    val data = SampleData.data("p90").await
    data.length should be(7)
  }
  
  test("summarize mean") {
    val data = SampleData.data("p90", max=1, summarize="mean").await
    data.length should be(1)
    data(0).value should be (26.714285714285715)
    data(0).time should be (DateTime.fromIsoDateTimeString("2013-01-19T22:13:40").get.clicks)
  }
  
  test("summarize max") {
    val data = SampleData.data("p90", max=1, summarize="max").await
    data.length should be(1)
    data(0).value should be (32)
    data(0).time should be (DateTime.fromIsoDateTimeString("2013-01-19T22:13:40").get.clicks)
  }
  
  test("summarize min") {
    val data = SampleData.data("p90", max=1, summarize="min").await
    data.length should be(1)
    data(0).value should be (20)
    data(0).time should be (DateTime.fromIsoDateTimeString("2013-01-19T22:14:10").get.clicks)
  }
  
  test("summarize uniques") {
    val data = SampleData.data("p90", max=1, summarize="uniques").await
    data.length should be(6)  // one duplicated item removed
    val dups = data.filter {datum => datum.value == 25} // duplicated item should be there, once
    assert(dups.length === 1)
    val last = data.filter {datum => datum.value == 20} // last unique item should be there
    assert(last.length === 1)
  }
  
  test("summarize sum") {
    val data = SampleData.data("p90", max=1, summarize="sum").await
    data.length should be(1)
    data(0).value should be (187)
    data(0).time should be (DateTime.fromIsoDateTimeString("2013-01-19T22:13:40").get.clicks)
  }
  
  test("summarize count") {
    val data = SampleData.data("p90", max=1, summarize="count").await
    data.length should be(1)
    data(0).value should be (7)
    data(0).time should be (DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get.clicks)
  }
  
  test("summarize count no data reduction") {
    val data = SampleData.data("p90", max=100, summarize="count").await
    data.length should be(7)
    data(0).value should be (1)
    data(0).time should be (DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get.clicks)
  }
  
  test("summarize unevenly time sampled data") {  
    // 7 data items over a 60 seconds period, but 3 data items in the first bucket
    val data = SampleDataUneven.data("p90", max=6, summarize="count").await
    data.length should be(4)
    data(0).value should be (3)
    data(0).time should be (DateTime.fromIsoDateTimeString("2013-01-19T22:13:10").get.clicks)
  }
  
  test("filter") {
    val data = SampleData.data("p90", valueFilter=Array(25)).await
    data.length should be(2)  // one duplicated item removed
    val dups = data.filter {datum => datum.value == 25} // duplicated item should be there, twice
    assert(dups.length === 2)
  }
  
}
