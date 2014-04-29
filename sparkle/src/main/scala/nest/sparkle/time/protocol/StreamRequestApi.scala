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

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.config.Config

import spray.json._

import nest.sparkle.store.Store
import nest.sparkle.time.transform.Transform
import nest.sparkle.util.ObservableFuture.WrappedObservable

/** Handle transformation requests from a v1 protocol StreamRequest. */
case class StreamRequestApi(val store: Store, val rootConfig:Config) extends SelectingSources {

  /** Process a StreamRequest message from the client, and return a future that completes with a Streams json object
   *  with the entire result set */
  def httpStreamRequest(streamRequest: StreamRequest)(implicit context: ExecutionContext): Future[Streams] = {
    case class StreamAndData(outputStream: JsonDataStream, data: Seq[JsArray])

    /** return a future that completes with the stream and data string together when the data string is ready */
    def streamWithFutureData[T](outputStream: JsonDataStream): Future[StreamAndData] = {
      outputStream.dataStream.toFutureSeq.map { seqChunks =>
        StreamAndData(outputStream, seqChunks.flatten)
      }
    }

    /** construct a Stream object from StreamAndData */
    def makeStream[T](streamAndData: StreamAndData): Stream = {
      Stream(
        streamId = 1L, // LATER make a real stream id
        metadata = streamAndData.outputStream.metadata.map{ _.asJson },
        data = Some(streamAndData.data),
        streamType = streamAndData.outputStream.streamType,
        end = Some(true)
      )
    }

    // await all the source columns, since we're processing over http
    val futureColumns = Future.sequence(sourceColumns(streamRequest.sources))
    val futureOutputStreams = // completes with Observable output streams
      Transform.connectTransform(streamRequest.transform, streamRequest.transformParameters, futureColumns)

    val futureStreamAndData = // completes when output streams are finished
      futureOutputStreams.flatMap { outputStreams =>
        val futureStreamAndDatas: Seq[Future[StreamAndData]] =
          outputStreams.map { streamWithFutureData(_) } // SCALA why can't we use infix here?
        Future.sequence(futureStreamAndDatas)
      }

    val futureStreamArray: Future[Array[Stream]] =
      futureStreamAndData map { seq =>
        seq.toArray.map { makeStream(_) }
      }

    futureStreamArray map { array => Streams(streams = array) }
  }

}
