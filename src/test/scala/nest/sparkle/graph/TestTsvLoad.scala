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
import scala.concurrent.ExecutionContext
import java.nio.file.Paths
import spray.util._

class TestTsvLoad extends FunSuite with Matchers {
  test("load A1 tsv file") {
    import ExecutionContext.Implicits.global
    val loadedFuture = FileLoadedDataSet.loadAsync(Paths.get("src/test/resources/tsv/d1/A1"))
    val dataSet = loadedFuture.await()
    dataSet.dataColumns.size should be (1)
    dataSet.time.length should be (158)
  }
}
