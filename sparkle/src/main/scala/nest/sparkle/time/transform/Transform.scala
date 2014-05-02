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

package nest.sparkle.time.transform

import scala.concurrent.{ ExecutionContext, Future }
import spray.json.DefaultJsonProtocol._
import spray.json._
import nest.sparkle.time.protocol.JsonDataStream
import nest.sparkle.store.Column
import nest.sparkle.time.transform.StandardColumnTransform.executeTypedTransform
import scala.util.Try
import scala.util.control.Exception._
import nest.sparkle.time.protocol.RangeParameters
import nest.sparkle.time.protocol.TransformParametersJson.RangeParamsFormat
import rx.lang.scala.Observable

case class TransformNotFound(msg: String) extends RuntimeException(msg)

/** apply transforms (to respond to StreamRequest messages) */
object Transform {

  /** apply requested StreamRequest transforms, returning OutputStreams that generate results on demand */
  def connectTransform( // format: OFF
        transform: String, transformParameters: JsObject,
        futureColumns: Future[Seq[Column[_, _]]]
      ) (implicit execution: ExecutionContext): Future[Seq[JsonDataStream]] = { // format: ON

    val futureStreams = transform match {
      case SummaryTransform(columnTransform) =>
        executeTypedTransform(futureColumns, columnTransform, transformParameters)
      case DomainRangeTransform(columnTransform) =>
        executeTypedTransform(futureColumns, columnTransform, transformParameters)
      case RawTransform(columnTransform) =>
        executeTypedTransform(futureColumns, columnTransform, transformParameters)
      // LATER handle application-pluggable custom transforms
      case _ => Future.failed(TransformNotFound(transform))
    }

    futureStreams
  }

  /** return typed RangeParameters from the untyped json transform parameters (in a StreamRequest message) */
  def rangeParameters[T: JsonFormat](transformParameters: JsObject): Try[RangeParameters[T]] = {
    catching(classOf[RuntimeException]).withTry {
      transformParameters.convertTo[RangeParameters[T]]
    }
  }
  

}

