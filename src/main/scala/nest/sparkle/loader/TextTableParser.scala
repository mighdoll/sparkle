package nest.sparkle.loader

import org.joda.time.format.ISODateTimeFormat
import scala.collection.mutable
import scala.collection.mutable.ArrayBuilder
import scala.util.control.Exception.allCatch
import scala.util.control.Exception.catching
import java.lang.{ Double => JDouble }
import java.lang.{ Long => JLong }
import scala.util.Try
import java.util.zip.DataFormatException
import scala.reflect.runtime.universe._

object TextTableParser {
  /** Parse all the data from an iterator that produces an array of strings and a column map that describes which
    * columns have which names.  Returns the time column data array and all other columns that can be parsed
    * as double precision numbers.  Non-numeric columns are discarded.
    */
  def parseRows(rows: Iterator[Array[String]], columnMap: ColumnMap): Try[RowInfo] = {

    def sortAndDiscardIndex[T, U: Ordering](seq: Seq[(T, U)]): Seq[T] = {
      val sorted = seq.sortBy{ case (value, index) => index }
      sorted.map { case (value, index) => value }
    }

    val rowNames: Seq[String] = {
      val withTime = ("key" -> columnMap.time) :: columnMap.data.toList
      sortAndDiscardIndex(withTime)
    }

    val columnTypes: Seq[TypeTag[_]] = {
      // for now, assume everything's a double except the index which is a long
      val doubleTags = columnMap.data.toList map { case (name, index) => (typeTag[Double], index) }
      val indexTag = typeTag[Long] -> columnMap.time
      sortAndDiscardIndex(indexTag :: doubleTags)
    }

    val rowIterator: Iterator[RowData] = {
      makeRowIterator(rows, columnMap)
    }

    Try {
      ConcreteRowInfo(names = rowNames,
        types = columnTypes,
        keyColumn = Some(columnMap.time),
        rows = rowIterator
      )
    }
  }

  /** return an iterator that will parse incoming string arrays into parsed numeric data */
  private def makeRowIterator(rows: Iterator[Array[String]], columnMap: ColumnMap): Iterator[RowData] = {
    val valueIndices:Seq[Int] = columnMap.data.map { case (name, index) => index }.toSeq
    
    /** an iterator that will pull data from the source parser */
    val iterator = new Iterator[RowData] {
      def hasNext: Boolean = rows.hasNext
      def next(): RowData = {
        val row = rows.next()
        val timeString = row(columnMap.time)
        val key = parseTime(timeString).getOrElse {
          formatError(s"couldn't parse the time in line ${row.mkString(",")}")
        }
        val values:Seq[Option[Any]] = valueIndices.map {index => 
          val valueString = row(index)
          val doubleValue = parseDouble(valueString) // LATER parse based on the type
          doubleValue
        }
        RowData(Some(key) +: values)
      }
    }
    
    iterator
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
  protected[loader] def parseTime(timeString: String): Option[Long] = {
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

  private def formatError(msg: String): Nothing = throw new DataFormatException(msg)

}