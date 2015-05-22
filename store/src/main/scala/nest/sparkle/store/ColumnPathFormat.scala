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

import scala.util.{Failure, Success, Try}

/**
 * For extracting various parts of a column path.
 *
 * Implementations must be thread safe and have a parameterless constructor.
 */
trait ColumnPathFormat {

  /**
   * Returns the column category of a column path, where column category refers to
   * what kind of column the column path represents, without taking into account any user,
   * device, etc specific info. E.g. the column category for column paths like
   * "dc1/server1/responseLatency/p99", "dc1/server2/responseLatency/p99", etc could be
   * "responseLatency/p99".
   *
   * @param columnPath name of the data set and column
   * @return           column category
   */
  def columnCategory(columnPath: String): Try[String]

  /**
   * Returns the entity path of a column path, where entity refers to the user, device,
   * etc specific info. Also returns the lookup keys for the entity path.
   * E.g. for column path "dc1/server1/responseLatency/p99", the entity path could be
   * "dc1/server1" and the associated lookup keys could be "dc1" and "server1" to allow
   * the entity to be looked up by datacenter name or server name.
   *
   * @param columnPath name of the data set and column
   * @return           entity path and associated lookup keys
   */
  def entity(columnPath: String): Try[Entity]

  /** Returns a column path given a column category and an entity path, or None if the
    * column category isn't applicable to the entity path. */
  def entityColumnPath(columnCategory: String, entityPath: String): Try[Option[String]]

  /** Returns a column path given a column category and a leaf dataSet, or None if the
    * column category isn't applicable to the leaf dataSet. */
  def leafDataSetColumnPath(columnCategory: String, dataSet: String): Try[Option[String]]
}

/** Entity info. */
case class Entity(entityPath: String, lookupKeys: Set[String])

/** Used when the entity path cannot be determined for a column path. */
case class EntityNotDeterminable(columnPath: String) extends RuntimeException(columnPath)

/** Used when the column category cannot be determined for a column path. */
case class ColumnCategoryNotDeterminable(columnPath: String) extends RuntimeException(columnPath)

/** Used when a column category cannot be found in the store. */
case class ColumnCategoryNotFound(columnCategory: String) extends RuntimeException(columnCategory)

/** Used when a column path cannot be determined when given a column category and
  * another input, like a entity path or a leaf dataSet */
case class ColumnPathNotDeterminable(columnCategory: String, input: String)
  extends RuntimeException(s"columnCategory=$columnCategory, input=$input")

/** Basic column path formatting. Implementations are free to override this. */
class BasicColumnPathFormat extends ColumnPathFormat {

  val columnCategoryRegex = "^.*/(.*)$".r
  val entityRegex1 = "^(.*/)?(.*)/(.*)/.*$".r
  val entityRegex2 = "^(.*)/.*$".r

  /** Returns the column path as the column category. */
  override def columnCategory(columnPath: String): Try[String] = {
    Success(columnPath)
  }

  /** If the column path only has one slash, whatever is before the trailing
    * slash will be returned as the entity path and as the lookup key.
    * If the column path has more than one slash, everything before the last slash
    * will be be returned as the entity path and whatever is between the third
    * from last slash (or beginning of string if such slash does not exist) and
    * the second from last slash will be returned as the lookup key.
    * eg:
    *   columnPath                          entityPath                      lookupKey
    *   epochs/p99                          epochs                          epochs
    *   march-27-0800/epochs/p99            march-27-0800/epochs            march-27-0800
    *   loadtests/march-27-0800/epochs/p99  loadtests/march-27-0800/epochs  march-27-0800
    * */
  override def entity(columnPath: String): Try[Entity] = {
    columnPath match {
      case entityRegex1(null, lookupKey, device) =>
        Success(Entity(s"$lookupKey/$device", Set(lookupKey)))
      case entityRegex1(head, lookupKey, device) =>
        Success(Entity(s"$head$lookupKey/$device", Set(lookupKey)))
      case entityRegex2(device)                  =>
        Success(Entity(s"$device", Set(device)))
      case _                                     =>
        Failure(EntityNotDeterminable(columnPath))
    }
  }

  /** Returns the column category if the entity path is a prefix of the column category */
  override def entityColumnPath(columnCategory: String, entityPath: String): Try[Option[String]] = {
    if (columnCategory.startsWith(s"$entityPath/")) Success(Some(columnCategory)) else Success(None)
  }

  /** Returns the column category if the leaf dataSet is a prefix of the column category */
  override def leafDataSetColumnPath(columnCategory: String, dataSet: String): Try[Option[String]] = {
    if (columnCategory.startsWith(s"$dataSet/")) Success(Some(columnCategory)) else Success(None)
  }
}
