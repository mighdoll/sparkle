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
import spray.http.DateTime
import scala.reflect.runtime.universe._

/** (for unit tests) a ram based Store with a sample column */
trait TestStore extends FunSuite with Matchers with ScalatestRouteTest
    with PropertyChecks with BeforeAndAfterAll {
  lazy val testId = "server1"

  lazy val testColumnName = "testColumn"
  lazy val testColumnPath = s"$testId/$testColumnName"
  lazy val testEvents = Seq(Event(100L, 1d), Event(200L, 2d))

  lazy val simpleColumnPath = s"$testId/simple"
  lazy val simpleMidpointMillis = DateTime.fromIsoDateTimeString("2013-01-19T22:13:40").get.clicks
  lazy val simpleEvents:Seq[Event[Long,Double]] = {
    val simpleData = Seq(  
      "2013-01-19T22:13:10" -> 25, 
      "2013-01-19T22:13:20" -> 26, 
      "2013-01-19T22:13:30" -> 31,
      "2013-01-19T22:13:40" -> 32, 
      "2013-01-19T22:13:50" -> 28, 
      "2013-01-19T22:14:00" -> 25, 
      "2013-01-19T22:14:10" -> 20)
    simpleData.map { case (time, value) =>
      val millis = DateTime.fromIsoDateTimeString(time).get.clicks
      Event(millis, value.toDouble)  
    }
  }

  lazy val store: WriteableRamStore = {
    val ramStore = new WriteableRamStore()
    addTestColumn(ramStore, testColumnPath, testEvents)
    addTestColumn(ramStore, simpleColumnPath, simpleEvents)    
    ramStore
  }

  private def addTestColumn[T:TypeTag,U:TypeTag](testStore:WriteableRamStore, 
      columnPath:String, events:Seq[Event[T,U]]) {
    val column = testStore.writeableColumn[T,U](columnPath).await
    column.write(events).await
  }
  
  implicit class IsoDateString(isoDateString:String) {
    def toMillis = {
      DateTime.fromIsoDateTimeString(isoDateString).get.clicks
    }
  }
  
}
