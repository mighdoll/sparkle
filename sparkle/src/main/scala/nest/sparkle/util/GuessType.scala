package nest.sparkle.util

import scala.reflect.runtime.universe._
import scala.reflect._
import scala.util.control.Exception._
import nest.sparkle.util.ParseStringTo.Implicits._
import nest.sparkle.util.ParseStringTo.{ BooleanParseException, StringToString }

/** utilities for guessing the type of a string */
object GuessType {

  /** guess at the type of a collection of strings. If the type of the string values
    * can't be narrowed to Int, Long, Double or Boolean, returns type String. If no
    * strings are present in the collection, also returns type String.
    */
  def parserTypeFromSampleStrings(values: Iterable[String]): ParseStringTo[_] = {
    values match {  // TODO mostly we want long for our test cases, so this is convenient for now. 
                    // Perhaps we should allow a type specifier in the .csv column header..
//      case Ints(parser)     => parser  
      case Longs(parser)    => parser
      case Doubles(parser)  => parser
      case Booleans(parser) => parser
      case _                => StringToString
    }
  }

  private abstract class MatchList[T: ParseStringTo, U <: Throwable: ClassTag] {
    val parser = implicitly[ParseStringTo[T]]
    def unapply(strings: Iterable[String]): Option[ParseStringTo[T]] = {
      val exceptionClass = classTag[U].runtimeClass
      val allParsed = catching(exceptionClass) opt {
        strings.foreach{ s =>
          parser.parse(s)
        }
      }
      allParsed.map { _ => parser }
    }
  }

  private object Ints extends MatchList[Int, NumberFormatException]
  private object Longs extends MatchList[Long, NumberFormatException]
  private object Doubles extends MatchList[Double, NumberFormatException]
  private object Booleans extends MatchList[Boolean, BooleanParseException]

}