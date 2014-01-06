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

package nest.sparkle.graph

import org.scalatest.FunSuite
import org.scalatest.Matchers
import java.nio.file.Files
import java.nio.file.Paths
import scala.concurrent.ExecutionContext
import spray.util._
import akka.util.Timeout
import scala.concurrent.duration._

class TestCsvLoad extends FunSuite with Matchers {
  test("load sample.csv file") {
    import ExecutionContext.Implicits.global
    val loadedFuture = FileLoadedDataSet.loadAsync(Paths.get("src/test/resources/sample.csv"))
    val dataSet = loadedFuture.await()
    dataSet.time.length should be (78)
    dataSet.dataColumns.size should be (15)
  }
  
  test("load csv file with numeric timestamps") {
    import ExecutionContext.Implicits.global
    val loadedFuture = FileLoadedDataSet.loadAsync(Paths.get("src/test/resources/epochs.csv"))
    val dataSet = loadedFuture.await(Timeout(1.hour))
    dataSet.time.length should be (2751)
    dataSet.dataColumns.size should be (3)
  }
  
  test("load csv file with numeric timestamps and no header") {
    import ExecutionContext.Implicits.global
    val loadedFuture = FileLoadedDataSet.loadAsync(Paths.get("src/test/resources/just-time.csv"))
    val dataSet = loadedFuture.await()
    dataSet.time.length should be (4)
    dataSet.dataColumns.size should be (0)
    val data = dataSet.data(metricName = "time", max = 1, summarize = "count").await()
    data.length should be (1)
    data(0).value should be (4)
  }

  test("test time parser") {
    FileLoadedDataSet.parseTime("2013-02-15T01:32:48.955") should be (Some(1360891968955L))
    FileLoadedDataSet.parseTime("1357710557000") should be (Some(1357710557000L))
    FileLoadedDataSet.parseTime("1373681685.0") should be (Some(1373681685000L))
  }

}
