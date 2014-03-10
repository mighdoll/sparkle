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

package nest.sparkle.store

import scala.concurrent.Future

/** a 'folder' that contains other datasets or columns */
trait DataSet {
  /** name of the dataset: legal characters are [a-ZA-Z0-9_-. ]+ */
  def name: String

  /** return a column in this dataset (or FileNotFound) */
  def column[T, U](columnName: String): Future[Column[T, U]]

  /** return all child columns */
  def childColumns: Future[Iterable[String]]

  /** return all child datasets */
  def childDataSets: Future[Iterable[DataSet]] = ??? // LATER
}
