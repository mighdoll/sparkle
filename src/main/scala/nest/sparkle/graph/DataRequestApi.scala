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

package nest.sparkle.graph

import scala.concurrent.Future
import nest.sparkle.graph.ParametersJson.SummarizeParamsFormat
import scala.util.Try
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.TryToFuture._
import scala.concurrent.ExecutionContext
import nest.sparkle.graph.ResponseJson.{ StreamFormat, SingleStreamResponseFormat }
import spray.json.JsObject
import nest.sparkle.store.cassandra.ObservableFuture._
import rx.lang.scala.Observable
import spray.json._
import spray.json.DefaultJsonProtocol._

/** handle transformation requests from a v1 protocol DataRequest */
case class DataRequestApi(storage: Storage) {

  /** */
  def request(dataRequest: DataRequest)(implicit context: ExecutionContext): Future[JsObject] = {
    type T = Long // TODO recover type from columnPath?
    type U = Double // TODO recover type from columnPath?

    val converted = Try {
      dataRequest.request.toLowerCase match {
        case "summarizemax" =>
          dataRequest.parameters.convertTo[SummarizeParams[T]]
      }
    }

    // get column data
    val futureObservable: Future[Observable[Event[T, U]]] =
      converted.toFuture.flatMap { params =>
        val columnPath = params.dataSet + "/" + params.column
        val columnFuture = storage.column[T, U](columnPath)
        columnFuture.map { column =>
          column.readRange()
        }
      }
    
    // TODO transform column with summary function
    val transformed = futureObservable

    // collect results into a SingleStreamResponse
    val futureJsObject =
      transformed.flatMap { stream: Observable[Event[T, U]] =>
        stream.toFutureSeq.map { events: Seq[Event[T, U]] =>
          val streamId = 1L // LATER make a stream id for 
          val data = events.map{event => Array(event.argument, event.value).toJson.asInstanceOf[JsArray]}
          val stream = Stream(streamId, data.toArray)
          SingleStreamResponse(stream).toJson.asInstanceOf[JsObject]
        }
      }

    futureJsObject
  }
}
