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

package nest.sparkle.loader

import scala.util.Try
import scala.util.Success
import java.util.zip.DataFormatException
import nest.sparkle.util.OptionConversion._
import nest.sparkle.util.Log

/** results from attempting to parse the first line as column headers */
case class ColumnHeaderMatch(val columnMap: Try[ColumnMap], matched: Boolean)

/** map column names to column index */
case class ColumnMap(key: Int, data: Map[String, Int]) { // LATER support loading files w/o a key field

  /** run a function over the names and indices of the value columns. Iteration 
   *  is in order that the columns appear in the file */
  def mapValueColumns[T](fn: (String, Int) => T): Seq[T] = {
    val sorted = data.toList.sortBy{ case (name, index) => index }
    sorted.map { case (name, index) => fn(name, index) }
  }
  
}

object ColumnHeaders extends Log {
  /** Parse the first line into a column map, synthesizing a map if the first line doesn't look like a header */
  def parseColumnHeader(line: Array[String]): ColumnHeaderMatch = {
    line.headOption match {
      case Some(columnText) if (isColumnHeader(columnText)) =>
        ColumnHeaderMatch(parseHeaderLine(line), true)
      case Some(columnText) =>
        ColumnHeaderMatch(Success(syntheticColumnHeaders(line)), false)
      case None => ???
    }
  }

  /** parse the first header line into a time column and any number of named data columns */
  private def parseHeaderLine(line: Array[String]): Try[ColumnMap] = {
    for {
      header <- validateColumnHeaders(line)
      timeColumn <- findTimeColumn(header)
      dataHeaders: Map[String, Int] = (header filter { case (_, index) => index != timeColumn }).toMap
    } yield {
      val columnMap = ColumnMap(timeColumn, dataHeaders)
      log.info(s"found columns: $columnMap")
      columnMap
    }
  }

  /** return a ColumnMap naming first column "time", and the other columns "1", "2", "3" */
  private def syntheticColumnHeaders(line: Array[String]): ColumnMap = {
    val dataMap = Iterator.range(1, line.tail.length).map{ index => index.toString -> index }.toMap
    ColumnMap(key = 0, data = dataMap)
  }

  /** return true if it seems like the first field is a header.  Current heuristic: if it starts with a letter */
  private def isColumnHeader(firstHeader: String): Boolean = {
    firstHeader.headOption match {
      case Some(c) if c.isLetter => true
      case Some(c)               => false
      case None                  => false
    }
  }

  /** verify that we have enough columns.
    * return the header row as a map
    */
  private def validateColumnHeaders(headers: Array[String]): Try[Map[String, Int]] = {
    for {
      _ <- Try { if (headers.length < 2) throw new DataFormatException("not enough columns") }
    } yield {
      headers.zipWithIndex.toMap
    }
  }

  /** Find a column for timestamps */
  private def findTimeColumn(header: Map[String, Int]): Try[Int] = {
    val optColumn = containsKey(header, "time", "Time", "epoch", "startDate", "startTime") orElse
      containsPartialKey(header, "time", "TIME", "Time", "date", "Date", "DATE")

    val keyColumnIndex = optColumn.getOrElse(0)
    Success(keyColumnIndex)
  }

  /** Search the map for one of a provided set of keys.  Return the first matching value in the map
    * or None if no matching key is found.
    */
  private def containsKey[K, V](map: Map[K, V], keys: K*): Option[V] = {
    keys.collectFirst {
      case key if map.contains(key) => map(key)
    }
  }

  /** Search the map for one of a provided set of keys.  Return the first matching value in the map
    * or None if no matching key is found.
    */
  private def containsPartialKey[V](map: Map[String, V], keys: String*): Option[V] = {
    // iterator that walks through the provided keys
    val matchingKeys = keys.toIterator flatMap { key =>
      // looking for a partial match in the map's keys
      map.keys.collect { case mapKey if mapKey.contains(key) => map(mapKey) }
    }

    matchingKeys.find(_ => true) // headOption
  }

}
