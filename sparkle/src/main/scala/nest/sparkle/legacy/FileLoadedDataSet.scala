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

import java.io.BufferedReader
import java.io.IOException
import java.lang.{Double => JDouble}
import java.lang.{Long => JLong}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.DataFormatException
import scala.Array.canBuildFrom
import scala.collection.mutable
import scala.collection.mutable.ArrayBuilder
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.Exception.allCatch
import scala.util.control.Exception.catching
import org.joda.time.format.ISODateTimeFormat
import com.github.nscala_time.time.Imports._
import au.com.bytecode.opencsv.CSVReader
import nest.sparkle.util.Opt
import nest.sparkle.util.OptionConversion._
import nest.sparkle.util.PeekableIterator
import java.io.IOException
import java.lang.{Double => JDouble}
import java.lang.{Long => JLong}
import nest.sparkle.util.Opt._

class FileLoadedDataSet(val name: String, val time: Array[Long],
                        val dataColumns: Iterable[RamDataColumn]) extends RamDataSet {
}

object FileLoadedDataSet {
  case class LoadedData(time: Array[Long], dataColumns: Iterable[RamDataColumn])

  /** load a csv/tsv file from a Path, return a Future that completes when the file is loaded.  */
  def loadAsync(path: Path, nameOpt: Opt[String] = None)(implicit context: ExecutionContext): Future[FileLoadedDataSet] = {
    Future {
      load(path, nameOpt)
    } flatMap { loadTry =>
      loadTry match {
        case Success(v)   => Future.successful(v)
        case Failure(err) => Future.failed(err)
      }
    }
  }

  /** load a csv/tsv file from the filesytem.  The returned data set will be named
   *  as provided by nameOpt, or by the trailing portionof the filename if that's
   *  not provided.  */
  private def load(path: Path, nameOpt: Opt[String] = None): Try[FileLoadedDataSet] = {
    val name = nameOpt.getOrElse {
      val tail = path.getName(path.getNameCount - 1)
      tail.toString.takeWhile(_ != '.')
    }
    val readerTry = catching(classOf[IOException]) withTry Files.newBufferedReader(path, StandardCharsets.UTF_8)
    val result = readerTry flatMap { reader => loadFromReader(reader, name) }
    readerTry.foreach(_.close())
    result
  }

  /** load a csv/tsv file */
  private def loadFromReader(reader: BufferedReader, name: String): Try[FileLoadedDataSet] = {
    readLines(reader) flatMap { lines =>
      if (lines.isEmpty) {
        Success(new FileLoadedDataSet(name, Array(), List())) // empty file, so return empty dataset
      } else {
        loadNonEmpty(name, lines)
      }
    }
  }

  /** Read the data from lines that have been column separated into arrays of strings */
  private def loadNonEmpty(name: String, lines: Iterator[Array[String]]): Try[FileLoadedDataSet] = {
    val peekableLines = PeekableIterator(lines)
    val parsedHeaders = parseColumnHeader(peekableLines.headOption.get) // caller knows it's non-empty
    val columnMap = parsedHeaders.columnMap
    val remaining = if (parsedHeaders.matched) peekableLines.tail else peekableLines

    columnMap.map { columnMap =>
      val loaded = parseLines(remaining, columnMap)
      new FileLoadedDataSet(name, loaded.time, loaded.dataColumns)
    }
  }

  /** results from attempting to parse the first line as column headers */
  private case class ColumnHeaderMatch(val columnMap: Try[ColumnMap], matched: Boolean)

  /** map column names to column index */
  private case class ColumnMap(time: Int, data: Map[String, Int])

  /** parse the first line into a column map, synthesizing a map if the first line doesn't look like a header */
  private def parseColumnHeader(line: Array[String]): ColumnHeaderMatch = {
    line.headOption match {
      case Some(columnText) if (isColumnHeader(columnText)) =>
        ColumnHeaderMatch(parseHeaderLine(line), true)
      case None => ???
      case _ =>
        ColumnHeaderMatch(Success(syntheticColumnHeaders(line)), false)
    }
  }

  /** parse the first header line into a time column and any number of named data columns */
  private def parseHeaderLine(line: Array[String]): Try[ColumnMap] = {
    for {
      header <- validateColumnHeaders(line)
      timeColumn <- findTimeColumn(header)
      dataHeaders: Map[String, Int] = (header filter { case (_, index) => index != timeColumn }).toMap
    } yield {
      ColumnMap(timeColumn, dataHeaders)
    }
  }

  /** return a ColumnMap naming first column "time", and the other columns "1", "2", "3" */
  private def syntheticColumnHeaders(line: Array[String]): ColumnMap = {
    val dataMap = Iterator.range(1, line.tail.length).map{ index => index.toString -> index }.toMap
    ColumnMap(time = 0, data = dataMap)
  }

  /** return true if it seems like the first field is a header.  Current heuristic: if it starts with a letter */
  private def isColumnHeader(firstHeader: String): Boolean = {
    firstHeader.headOption match {
      case Some(c) if c.isLetter => true
      case Some(c)               => false
      case None                  => false
    }
  }

  /** Returns an Iterator that incrementally reads from a BufferedReader and reports arrays of csv separated data.  */
  private def csvIterator(reader: BufferedReader): Iterator[Array[String]] = {
    val csvReader = new CSVReader(reader)

    new Iterator[Array[String]] {
      private var nextElem: Option[Array[String]] = None

      advance()

      def next(): Array[String] = {
        val current = nextElem.get
        advance()
        current
      }

      def hasNext: Boolean = {
        nextElem.isDefined
      }

      private def advance(): Unit = {
        nextElem = Option(csvReader.readNext())
      }

    }
  }

  /** read lines into columns using either tsv or csv format */
  private def readLines(reader: BufferedReader): Try[Iterator[Array[String]]] = {
    reader.mark(10000)
    val firstLine = reader.readLine()
    val tried = Try[Iterator[Array[String]]] {
      reader.reset()
      firstLine match {
        case null => Iterator()
        case _ if firstLine.contains('\t') =>
          TsvReader(reader).lines()
        case _ =>
          csvIterator(reader)
      }
    }
    tried
  }


  /** Parse all the data from an iterator that produces an array of strings and a column map that describes which
    * columns have which names.  Returns the time column data array and all other columns that can be parsed
    * as double precision numbers.  Non-numeric columns are discarded.
    */
  private def parseLines(lines: Iterator[Array[String]], columnMap: ColumnMap): LoadedData = {
    val allColumns = columnMap.data.map { case (name, index) => index }
    val activeColumnIndices = mutable.Set[Int](allColumns.toSeq: _*)
    val columnCount = (allColumns.toList.length + 1)
    val columnBuilders = (0 to columnCount).map { _ => new ArrayBuilder.ofDouble() }.toArray
    val timeBuilder = new ArrayBuilder.ofLong()

    for { // foreach column value in each line
      line <- lines
      time <- parseTime(line(columnMap.time)) orElse {
        Console.err.println(s"couldn't parse the time in line ${line.mkString(",")}"); None // throw here?
      }
      _ = timeBuilder += time
      columnIndex <- activeColumnIndices.toArray
      columnValue = line(columnIndex)
    } {
      parseDouble(columnValue) match {
        case Some(double) => // add the numeric to the column data
          val builder = columnBuilders(columnIndex)
          builder += double
        case None => // remove columns that contain non-numeric or blank data
          activeColumnIndices.remove(columnIndex)
      }
    }

    val time = timeBuilder.result.toArray
    val dataColumns: Iterable[RamDataColumn] =
      activeColumnIndices.toIterable map { columnIndex =>
        val data = columnBuilders(columnIndex).result.toArray
        val nameEntry = columnMap.data.collectFirst {
          case (name, index) if index == columnIndex => name
        }
        val name: String = nameEntry.get
        RamDataColumn(name, data)
      }

    LoadedData(time, dataColumns)
  }

  object IsoDateParse {
    import com.github.nscala_time.time.Imports._
    val isoParser = ISODateTimeFormat.dateHourMinuteSecondMillis().withZoneUTC()
    def unapply(string: String): Option[Long] = {
      isoParser.parseOption(string) map (_.millis)
    }
  }

  object LongParse {
    def unapply(string: String): Option[Long] = allCatch opt JLong.parseLong(string)
  }

  object DoubleParse {
    def unapply(string: String): Option[Double] = allCatch opt JDouble.parseDouble(string)
  }

  /** Parse a date in epoch seconds, epoch milliseconds or in the format:  2013-02-15T01:32:50.186 */
  protected[legacy] def parseTime(timeString: String): Option[Long] = {
    timeString match {
      case IsoDateParse(millis) => Some(millis)
      case LongParse(millis)    => Some(millis)
      case DoubleParse(seconds) => Some((seconds * 1000.0).toLong)
      case _                    => None
    }
  }

  /** optionally parse a double */
  private def parseDouble(str: String): Option[Double] = {
    allCatch opt JDouble.parseDouble(str)
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
    val optColumn = containsKey(header, "time", "Time", "startDate", "startTime") orElse
      containsPartialKey(header, "time", "TIME", "Time", "date", "Date", "DATE")

    optColumn.toTryOr(new DataFormatException(s"time column not found in:  [${header.keys.mkString(", ")}]"))
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
