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
import nest.sparkle.util.OptionConversion._
import scala.concurrent.Future
import scala.concurrent.Promise
import java.io.FileNotFoundException
import nest.sparkle.store.Storage
import nest.sparkle.store.DataSet
import nest.sparkle.store.Column

/** A DataRegistry backed by a preloaded set of DataSets  */
case class PreloadedRegistry(dataSetList:Iterable[DataSetOld])
    (implicit val execution:ExecutionContext) extends DataRegistry {
  private val dataSets = dataSetList.map(dataSet => (dataSet.name, dataSet)).toMap
  def allSets():Future[Iterable[String]] = 
    Promise.successful(dataSets.keys).future
    
  def findDataSet(name:String):Future[DataSetOld] = 
    dataSets.get(name).futureOrFailed(new FileNotFoundException(s"dataSet $name not found"))
    
}

case class PreloadedStore(dataSetList:Iterable[DataSetOld]) extends Storage {
  /** return the dataset for the provided dataSet name or path (fooSet/barSet/mySet).  */
  def dataSet(name: String): Future[DataSet] = ???

  /** return a column from a columnPath like fooSet/barSet/columName*/
  def column[T, U](columnPath: String): Future[Column[T, U]] = ???
}
