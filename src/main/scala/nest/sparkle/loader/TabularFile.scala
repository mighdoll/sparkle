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

import java.io.BufferedReader
import java.io.IOException
import java.lang.{ Double => JDouble }
import java.lang.{ Long => JLong }
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.Array.canBuildFrom
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.github.nscala_time.time.Imports._
import au.com.bytecode.opencsv.CSVReader
import nest.sparkle.util.TryToFuture._
import java.io.IOException
import rx.lang.scala.Observable
import scala.reflect.runtime.universe._
import nest.sparkle.store.Event
import scala.language.existentials
import nest.sparkle.util.PeekableIterator
import nest.sparkle.loader.ColumnHeaders._
import java.io.Closeable

/** row metadata.  names and types include the key column */
trait RowInfo {
  val names: Seq[String]
  val types: Seq[TypeTag[_]]
  val keyColumn: Option[Int]
  val rows: Iterator[RowData]
}

case class ConcreteRowInfo(names: Seq[String], types: Seq[TypeTag[_]], keyColumn: Option[Int], rows: Iterator[RowData])
  extends RowInfo

case class CloseableRowInfo(names: Seq[String], types: Seq[TypeTag[_]], keyColumn: Option[Int], rows: Iterator[RowData], closeable: Closeable)
    extends RowInfo with Closeable {
  def close() = closeable.close()
}

/** row contents.  Rows typically contain a key value (typically time), and zero or more other values */
case class RowData(values: Seq[Option[Any]]) {

  def key(rowInfo: RowInfo): Option[Any] = {
    rowInfo.keyColumn map { column =>
      values(column).get
    }
  }

}

object TabularFile {
  /** load a csv/tsv file from a Path, return a Future that completes when the file is loaded.  */
  def load(path: Path, format: FileFormat = UnknownFormat)(implicit context: ExecutionContext): Future[CloseableRowInfo] = { // make Observable?
    val tried =
      for {
        reader <- Try { Files.newBufferedReader(path, StandardCharsets.UTF_8) }
        rowInfo <- loadFromReader(reader)
      } yield {
        CloseableRowInfo(rowInfo.names, rowInfo.types, rowInfo.keyColumn, rowInfo.rows, reader)
      }
    tried.toFuture
  }

  /** load a csv/tsv file */
  private def loadFromReader(reader: BufferedReader): Try[RowInfo] = {
    lineTokens(reader) flatMap { lines =>
      if (lines.isEmpty) {
        Success(ConcreteRowInfo(Nil, Nil, None, Iterator.empty))
      } else {
        loadNonEmpty(lines)
      }
    }
  }

  /** Read the data from lines that have been column separated into arrays of strings */
  private def loadNonEmpty(lines: Iterator[Array[String]]): Try[RowInfo] = {
    val peekableLines = PeekableIterator(lines)
    val parsedHeaders = parseColumnHeader(peekableLines.headOption.get)
    val columnMap = parsedHeaders.columnMap
    val remaining = if (parsedHeaders.matched) peekableLines.tail else peekableLines

    columnMap.flatMap { columnMap =>
      TextTableParser.parseRows(remaining, columnMap)
    }
  }

  /** Returns an Iterator that incrementally reads from a BufferedReader
    * and reports arrays of strings from comma or tab separated lines.
    */
  private def csvIterator(reader: BufferedReader, separator: Char = ','): Iterator[Array[String]] = {
    val csvReader = new CSVReader(reader, separator)

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

  /** Returns an iterator that returns an array of the
    * comma or tab separated values in the file.  The separator (tab or comma)
    * is guessed by looking at the first line in the file.
    */
  private def lineTokens(reader: BufferedReader): Try[Iterator[Array[String]]] = { // Observable or other stream construct
    reader.mark(10000) // mark our spot, so we can reset back to the beginning
    val firstLine = reader.readLine()
    val tried = Try[Iterator[Array[String]]] {
      reader.reset() // back to the beginning
      firstLine match {
        case null => Iterator()
        case _ if firstLine.contains('\t') =>
          csvIterator(reader, '\t')
        case _ =>
          csvIterator(reader)
      }
    }
    tried
  }

}
