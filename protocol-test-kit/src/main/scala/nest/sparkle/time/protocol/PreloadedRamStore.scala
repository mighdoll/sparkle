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

import scala.reflect.runtime.universe._

import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import spray.testkit.ScalatestRouteTest

import spray.util._

import nest.sparkle.store.{ReadWriteStore, Event}
import nest.sparkle.store.cassandra.RecoverCanSerialize
import nest.sparkle.store.ram.WriteableRamStore

trait PreloadedRamService extends PreloadedRamStore with TestDataService {
  override def readWriteStore = writeableRamStore
}

/** (for unit tests) a ram based Store with a sample column */
trait PreloadedRamStore extends FunSuite with Matchers with ScalatestRouteTest
    with PropertyChecks with BeforeAndAfterAll with StreamRequestor {
  lazy val testId = "server1"

  lazy val testColumnName = "testColumn"
  lazy val testColumnPath = s"$testId/$testColumnName"
  lazy val testEvents = Seq(Event(100L, 1d), Event(200L, 2d))
  override def defaultColumnPath: String = testColumnPath

  lazy val simpleColumnPath = s"$testId/simple"
  lazy val simpleMidpointMillis = "2013-01-19T22:13:40Z".toMillis
  lazy val simpleEvents: Seq[Event[Long, Double]] = {
    val data = Seq(
      "2013-01-19T22:13:10Z" -> 25,
      "2013-01-19T22:13:20Z" -> 26,
      "2013-01-19T22:13:30Z" -> 31,
      "2013-01-19T22:13:40Z" -> 32,
      "2013-01-19T22:13:50Z" -> 28,
      "2013-01-19T22:14:00Z" -> 25,
      "2013-01-19T22:14:10Z" -> 20)

    stringTimeEvents(data)
  }

  private def stringTimeEvents(events: Seq[(String, Int)]): Seq[Event[Long, Double]] = {
    events.map {
      case (time, value) =>
        val millis = time.toMillis
        Event(millis, value.toDouble)
    }
  }

  lazy val unevenColumnPath = s"$testId/uneven"
  
  lazy val unevenEvents: Seq[Event[Long, Double]] = {
    val data = Seq(
      "2013-01-19T22:13:10Z" -> 26,
      "2013-01-19T22:13:11Z" -> 26,
      "2013-01-19T22:13:12Z" -> 31,
      "2013-01-19T22:13:40Z" -> 32,
      "2013-01-19T22:13:50Z" -> 28,
      "2013-01-19T22:14:00Z" -> 25,
      "2013-01-19T22:14:10Z" -> 20)
    stringTimeEvents(data)
  }

  lazy val writeableRamStore: WriteableRamStore = {
    val ramStore = new WriteableRamStore()
    addTestColumn(ramStore, testColumnPath, testEvents)
    addTestColumn(ramStore, simpleColumnPath, simpleEvents)
    addTestColumn(ramStore, unevenColumnPath, unevenEvents)
    ramStore
  }

  private def addTestColumn[T: TypeTag, U: TypeTag](testStore: WriteableRamStore,
                                                    columnPath: String, events: Seq[Event[T, U]]) {
    implicit val keyTag = RecoverCanSerialize.tryCanSerialize[T](typeTag[T]).get
    implicit val valueTag = RecoverCanSerialize.tryCanSerialize[U](typeTag[U]).get
    val column = testStore.writeableColumn[T, U](columnPath).await
    column.write(events).await
  }

  /** for test convenience enhance String with a toMillis method that converts iso 8601 strings
    * into milliseconds
    */
  // TODO DRY with StringToMillis
  implicit class IsoDateString(isoDateString: String) {
    import com.github.nscala_time.time.Implicits._

    def toMillis = isoDateString.toDateTime.getMillis
  }

}
