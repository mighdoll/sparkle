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

package nest.sparkle.store.cassandra
import ColumnSupport._

object ColumnSupport {
  def constructColumnPath(dataSetName:String, columnName:String): String = dataSetName + "/" + columnName
}


/** Common code for creating cassandra Column Reader and Writer implementations */
trait ColumnSupport {
  def dataSetName:String
  def columnName:String

  lazy val columnPath = constructColumnPath(dataSetName, columnName)
  protected val rowIndex = 0.asInstanceOf[AnyRef] // LATER implement when we do wide row partitioning

}
