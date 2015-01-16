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
package nest.sparkle.loader

import nest.sparkle.loader.Loader._
import nest.sparkle.util.KindCast._

import scala.reflect.runtime.universe._
import scala.language.existentials
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/** contains the event decoder and meta data decoders to convert a stream of
  * of records into a stream of sparkle data events.
  *
  * The columnPath produced by users of the decoder is expected to be:
  * prefix/id/suffix/columnName
  */
trait ColumnDecoder {
  /** return the full columnPath for this decoder given an id and columnName */
  /** columnPath after the id (including the columnName itself) */
  lazy val columnPathSuffix: String = appendSlash(suffix)

  /** columnPath before the id */
  lazy val columnPathPrefix: String = appendSlash(prefix)

  /** subclass should override to add a suffix to the columnPath */
  protected def suffix: String = ""

  /** columnPath prefix, without trailing slash */
  protected def prefix: String = ""

  /** append a slash if the string is non-empty */
  private def appendSlash(string: String): String = {
    if (string == "") {
      string
    } else {
      string + "/"
    }
  }

  /** return the full columnPath for this decoder given an id and columnName */
  def columnPath(id:String, columnName:String):String =
    s"$columnPathPrefix$id/$columnPathSuffix$columnName"
}

/** A ColumnDecoder for records that contain a single id and multiple rows,
  * where each row contains a single key and multiple values.
  *
  * e.g. a record of the following structure:
  * id: "id",
  * row: [ [key,[value,..],
  *        [key,[value,..]
  *      ]
  */
trait KeyValueColumn extends ColumnDecoder {
  /** report the types of the id, the key, and the values */
  def metaData: ArrayRecordMeta
}

object KeyValueColumnConverter {

  /**
   * Returns a Try with ColumnDecoderException for decoding errors and
   * with ColumnPathNotDeterminable if the column path cannot be determined.
   */
  def convertMessage(decoder: KeyValueColumn,
                     record: ArrayRecordColumns): Try[TaggedBlock] = {
    // Wrap a try/catch around the whole method so no error crashes the loader.
    try {
      val columnPathIds = {
        val ids = decoder.metaData.ids zip record.ids flatMap { case (NameTypeDefault(name, typed, default), valueOpt) =>
          // What happens if the original value was explicitly null?
          val valueOrDefault = valueOpt orElse default orElse {
            throw ColumnPathNotDeterminable(s"record contains field $name with no value and no default")
          }
          valueOrDefault.map(_.toString) // used to use TagTypeUtils
        }
        ids.foldLeft("")(_ + "/" + _).stripPrefix("/")
      }

      val block =
        record.typedColumns(decoder.metaData).map { taggedColumn =>
          val columnPath = decoder.columnPath(columnPathIds, taggedColumn.name)

          /** do the following with type parameters matching each other
            * (even though our caller will ultimately ignore them) */
          def withFixedTypes[T, U]() = {
            val typedEvents = taggedColumn.events.asInstanceOf[Events[T, U]]
            val keyType: TypeTag[T] = castKind(taggedColumn.keyType)
            val valueType: TypeTag[U] = castKind(taggedColumn.valueType)
            TaggedSlice[T, U](columnPath, typedEvents)(keyType, valueType)
          }
          withFixedTypes[Any, Any]()
        }

      log.trace(
        s"convertMessage: got block.length ${block.length}  head:${
          block.headOption.map {_.shortPrint(3)}
        }"
      )
      Success(block)
    } catch {
      case e: ColumnPathNotDeterminable => Failure(e)
      case NonFatal(err)                => Failure(ColumnDecoderException(err))
    }
  }
}

/** Indicates a decoding issue */
case class ColumnDecoderException(cause: Throwable) extends RuntimeException(cause)

/** Used when the column path cannot be determined for a message */
case class ColumnPathNotDeterminable(msg: String) extends RuntimeException(msg)
