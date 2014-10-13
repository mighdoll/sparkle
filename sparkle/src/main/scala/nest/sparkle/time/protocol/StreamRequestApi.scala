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

import scala.concurrent.{ ExecutionContext, Future }
import com.typesafe.config.Config
import spray.json._
import nest.sparkle.store.Store
import nest.sparkle.util.ObservableFuture._
import rx.lang.scala.Observable
import akka.actor.ActorSystem
import akka.actor.ActorRefFactory
import unfiltered.netty.websockets.WebSocket
import nest.sparkle.time.protocol.ResponseJson._
import io.netty.channel.ChannelFuture
import nest.sparkle.util.RandomUtil
import nest.sparkle.util.TryToFuture._
import nest.sparkle.util.ObservableUtil

// TODO break this up into something per session/socket and something per service for
// looking up transforms
/** Handle transformation requests from a v1 protocol StreamRequest. */
case class StreamRequestApi(val store: Store, val rootConfig: Config) // format: OFF
    (implicit actorFactory:ActorRefFactory)
    extends SelectingSources with SelectingTransforms { // format: ON
  val authProvider = AuthProvider.instantiate(rootConfig) 
  
  /** handle a StreamRequestMessage over a websocket */
  def socketStreamRequest(request: StreamRequestMessage, socket: WebSocket) // format: OFF 
    (implicit context: ExecutionContext):Unit = { // format: ON
    val futureOutputStreams = outputStreams(request)

    // TODO assign streamIds to each output stream
    // TODO respect sendUpdates

    // read first block off each output stream, and send as part of Streams
    val streamsSent = sendStreams(request, futureOutputStreams, socket)

    // send subsequent data blocks as Update messages
    val updatesToo = streamsSent.flatMap { remaining =>
      sendUpdates(request, remaining, socket)
    }
    updatesToo.subscribe() // don't forget to kick lazy observables into action
  }

  /** send an initial Streams response over a weboscket */
  private def sendStreams(request: StreamRequestMessage,
                          futureOutputStreams: Future[Seq[JsonDataStream]],
                          socket: WebSocket)(implicit context: ExecutionContext): Observable[Seq[JsonDataStream]] = {

    // first data chunk from each column transform's json stream, and the
    val headsAndRemaining =
      for {
        seqJson <- futureOutputStreams.toObservable
        jsonStream <- Observable.from(seqJson)
        (dataHead, dataTail) = ObservableUtil.headTail(jsonStream.dataStream)
        first <- dataHead
      } yield {
        val remaining = jsonStream.copy(dataStream = dataTail)
        StreamDataRemaining(jsonStream, first, remaining)
      }

    val streamHeads = headsAndRemaining.map {
      case StreamDataRemaining(jsonStream, first, remaining) =>
        val stream = makeStream(StreamAndData(jsonStream, first), end = false)
        (stream, remaining)
    }

    val streamsSent =
      streamHeads.toSeq.map { seq =>
        val streamSeq = seq.map{ case (stream, _) => stream }
        val remaining = seq.map{ case (_, remaining) => remaining }
        val streams = Streams(streamSeq)
        val streamsMessage = StreamsMessage(
          requestId = request.requestId,
          realm = request.realm.map{ orig => RealmToClient(orig.name)},
          traceId = request.traceId,
          messageType = MessageType.Streams,
          message = streams
        )
        log.info(s"StreamsMessage ${streamsMessage.takeData(3).toJson.compactPrint}")
        socket.send(streamsMessage.toJson.prettyPrint)
        remaining
      }
    streamsSent
  }

  /** send Update messages over a websocket */
  private def sendUpdates(request: StreamRequestMessage,
                          remaining: Seq[JsonDataStream],
                          socket: WebSocket)(implicit context: ExecutionContext): Observable[ChannelFuture] = {

    val tails = // second and subsequent chunks from each transform's json stream
      for {
        jsonStream <- Observable.from(remaining)
        nextChunk <- jsonStream.dataStream
      } yield {
        log.info(s"sendUpdates got chunk: $nextChunk")
        StreamAndData(jsonStream, nextChunk)
      }

    val updates =
      tails.map { streamAndData =>
        makeUpdate(streamAndData)
      }

    val updatesSent = {
      updates.map { update =>
        val traceId = {
          val requestId = request.traceId.map(_ + "-").getOrElse("")
          requestId + RandomUtil.randomAlphaNum(3)
        }
        val realmToClient = request.realm.map{orig => RealmToClient(orig.name)}
        val updateMessage = UpdateMessage(
          requestId = None, // TODO should we align with original requestID?
          realm = realmToClient,
          traceId = Some(traceId),
          messageType = MessageType.Update,
          message = update
        )
        log.info(s"UpdateMessage ${updateMessage.takeData(3).toJson.compactPrint}")
        socket.send(updateMessage.toJson.prettyPrint)
      }
    }

    updatesSent
  }

  case class StreamAndData(outputStream: JsonDataStream, data: Seq[JsArray])
  case class StreamDataRemaining(headStream: JsonDataStream, data: Seq[JsArray], remaining: JsonDataStream)

  /** Process a StreamRequest message from the client, and return a future that completes with a Streams json object
    * with the entire result set
    */
  def httpStreamRequest(streamRequestMessage: StreamRequestMessage)(implicit context: ExecutionContext): Future[Streams] = {

    /** return a future that completes with the stream and data string together when the data string is ready */
    def streamWithFutureData[T](outputStream: JsonDataStream): Future[StreamAndData] = {
      // over http, we don't want ongoing updates, just the initial data
      val initial = outputStream.dataStream.first
      initial.toFutureSeq.map { seqChunks =>
        StreamAndData(outputStream, seqChunks.flatten)
      }
    }

    val futureOutputStreams = outputStreams(streamRequestMessage)
    val futureStreamAndData = // completes when output streams are finished, since we're over http
      futureOutputStreams.flatMap { outputStreams =>
        val futureStreamAndDatas: Seq[Future[StreamAndData]] =
          outputStreams.map { streamWithFutureData(_) } // SCALA why can't we use infix here?
        Future.sequence(futureStreamAndDatas)
      }

    val futureStreamArray: Future[Array[Stream]] =
      futureStreamAndData map { seq =>
        seq.toArray.map { data => makeStream(data, end = true) }
      }

    futureStreamArray map { array =>
      Streams(streams = array)
    }
  }

  /** construct a Stream object from StreamAndData */
  private def makeStream[T](streamAndData: StreamAndData, end: Boolean): Stream = {
    Stream(
      streamId = 1L, // TODO make a real stream id
      metadata = streamAndData.outputStream.metadata.map{ _.asJson },
      data = Some(streamAndData.data),
      streamType = streamAndData.outputStream.streamType,
      end = Some(end)
    )
  }

  private def makeUpdate[T](streamAndData: StreamAndData): Update = {
    Update(
      streamId = 1L, // TODO make a real stream id
      data = Some(streamAndData.data),
      end = Some(false)
    )
  }

  /** return an future output stream for each column */
  private def outputStreams(streamRequestMessage: StreamRequestMessage) // format: OFF
      (implicit context: ExecutionContext): Future[Seq[JsonDataStream]] = { // format: ON
    val futureAuthorizer = authProvider.authenticate(streamRequestMessage.realm)
    futureAuthorizer.flatMap { authorizer =>
      val request = streamRequestMessage.message
      val futureGroups = sourceColumns(request.sources, authorizer)
      // completes with Observable output streams
      selectAndRunTransform(request.transform, request.transformParameters, futureGroups)
    }
  }

}
