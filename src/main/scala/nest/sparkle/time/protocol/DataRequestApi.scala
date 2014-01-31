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

package nest.sparkle.time.protocol

import scala.concurrent.Future
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.TryToFuture._
import scala.concurrent.ExecutionContext
import nest.sparkle.util.ObservableFuture._
import spray.json._
import spray.json.DefaultJsonProtocol._
import nest.sparkle.store.Storage
import nest.sparkle.time.transform.Transform

/** Handle transformation requests from a v1 protocol DataRequest. */
case class DataRequestApi(storage: Storage) {

  /** Process a StreamRequest message from the client, and return a future that completes with a Streams json object */
  def handleStreamRequest(streamRequest: StreamRequest)(implicit context: ExecutionContext): Future[Streams] = {
    case class StreamAndData(outputStream: JsonDataStream, data: Seq[JsArray])

    /** return a future that completes with the stream and data string together when the data string is ready */
    def streamWithFutureData[T](outputStream: JsonDataStream): Future[StreamAndData] = {
      outputStream.dataStream.toFutureSeq.map {seqChunks =>
        StreamAndData(outputStream, seqChunks.flatten)
      }
    }

    /** construct a Stream object from StreamAndData */
    def makeStream[T](streamAndData: StreamAndData): Stream = {
      Stream(
        streamId = 1L, // LATER make a real stream id
        source = "TBD".toJson,  // LATER match up source with requesting source
        label = streamAndData.outputStream.label.map{ _.asJson },
        data = Some(streamAndData.data),
        streamType = streamAndData.outputStream.streamType,
        end = Some(true)
      )
    }

    val futureColumns = SourceSelector.sourceColumns(streamRequest.sources, storage)
    val futureOutputStreams = // completes with Observable output streams
      Transform.connectTransform(streamRequest.transform, streamRequest.transformParameters, futureColumns)

    val futureStreamAndData = // completes when output streams are finished
      futureOutputStreams.flatMap { outputStreams =>
        val futureStreamAndDatas: Seq[Future[StreamAndData]] = 
          outputStreams.map { streamWithFutureData(_) } // SCALA why can't we use infix here?
        Future.sequence(futureStreamAndDatas)
      }

    val futureStreamArray:Future[Array[Stream]] = 
      futureStreamAndData map { seq =>
        seq.toArray.map { makeStream(_) }
      }

    futureStreamArray map { array => Streams(streams = array) }  
  }

}
