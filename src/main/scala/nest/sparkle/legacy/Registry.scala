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

import rx.lang.scala.Observable

/** A queryable collection of DataSets.
  * Intended for e.g. UI dashboards to display or autocomplete the list of DataSets.
  */
trait Registry {
  /** return a potentially long list of all root level level datasets */
  def allDataSets: Observable[String]

  /** search for dataSets with partial text, e.g. for autocomplete */
  def filterDataSets(partial: String): Observable[String]
}
