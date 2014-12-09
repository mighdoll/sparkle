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

import scala.util.{Success, Try}

/**
 * For determining the column category of a column path, where column category refers to
 * what kind of column the column path represents, without taking into account any user,
 * device, etc specific info. E.g. the column category for column paths like
 * "server1/responseLatency/p99", "server2/responseLatency/p99", etc could be
 * "responseLatency/p99".
 *
 * Implementations must be thread safe and have a parameterless constructor.
 */
trait ColumnPathFormat {

  /**
   * Returns the column category of a column path.
   *
   * @param columnPath name of the data set and column, e.g. "server1/responseLatency/p99"
   * @return           column category, e.g. "responseLatency/p99"
   */
  def getColumnCategory(columnPath: String) : Try[String]
}

/**
 * Used when the column category cannot be determined for a column path.
 */
case class ColumnCategoryNotDeterminable(columnPath: String) extends RuntimeException(columnPath)

/**
 * Used when a column category cannot be found in the store.
 */
case class ColumnCategoryNotFound(columnCategory: String) extends RuntimeException(columnCategory)

/**
 * Doesn't attempt to format the column path in any special way, e.g. just returns the
 * column path as the column category.
 */
class BasicColumnPathFormat extends ColumnPathFormat {

  /**
   * Returns the column path as the column category.
   */
  def getColumnCategory(columnPath: String) : Try[String] = {
    Success(columnPath)
  }
}

