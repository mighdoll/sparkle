/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.time.protocol

import org.scalatest.FunSuite
import org.scalatest.Matchers
import spray.testkit.ScalatestRouteTest
import org.scalatest.prop.PropertyChecks
import org.scalatest.BeforeAndAfterAll
import nest.sparkle.store.ram.WriteableRamStore
import spray.util._
import scala.concurrent.ExecutionContext
import nest.sparkle.store.Event
import nest.sparkle.store.Storage
import nest.sparkle.store.WriteableStorage

/** (for unit tests) a ram based Store with a sample column */
trait TestStore extends FunSuite with Matchers with ScalatestRouteTest
    with PropertyChecks with BeforeAndAfterAll {
  
  lazy val testColumn = "latency.p99"
  lazy val testId = "server1"
  lazy val testColumnPath = s"$testId/$testColumn"

  lazy val store: WriteableRamStore = {
    val store = new WriteableRamStore()
    val column = store.writeableColumn[Long, Double](testColumnPath).await
    column.create("a test column").await

    val writeColumn = store.writeableColumn[Long, Double](testColumnPath).await
    writeColumn.write(Seq(Event(100L, 1), Event(200L, 2))).await

    store
  }

}