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

package nest.sparkle.loader.kafka

import com.typesafe.config.Config
import kafka.consumer.ConsumerConfig
import kafka.consumer.ConsumerConnector
import kafka.consumer.Consumer
import kafka.serializer.Decoder
import kafka.consumer.KafkaStream
import com.typesafe.config.ConfigFactory
import rx.lang.scala.Observable
import nest.sparkle.util.ObservableIterator._
import scala.concurrent.ExecutionContext
import nest.sparkle.loader.kafka.KafkaDecoders.Implicits._
import nest.sparkle.util.ConfigUtil
import kafka.consumer.ConsumerTimeoutException
import nest.sparkle.util.Log
import nest.sparkle.util.RecoverableIterator
import nest.sparkle.util.RecoverableIterator
import kafka.consumer.ConsumerIterator
import kafka.consumer.Whitelist

/** Enables reading a stream from a kafka topics.
  *
  * The reader exposes only a single topic from its underlying connection so that
  * it can record its kafka read queue position (via commitOffsets) synchronously
  * with the consumers of the stream of this topic.
  *
  * @param topic  kafka topic to read from
  * @param consumerGroup - allows setting kafka consumerGroup per KafkaReader
  * @param config contains settings for the kafka client library. must contain a "kafka-reader" key.
  */
class KafkaReader[T: Decoder](topic: String, rootConfig: Config = ConfigFactory.load(),
                              consumerGroupPrefix: Option[String]) extends Log {
  lazy val consumerConfig = {
    val properties = {
      val loaderConfig = rootConfig.getConfig("sparkle-time-server.kafka-loader")

      // extract the kafka-client settings verbatim, send directly to kafka
      val props = ConfigUtil.properties(loaderConfig.getConfig("kafka-reader"))

      val groupPrefix = consumerGroupPrefix.getOrElse { loaderConfig.getString("reader.consumer-group-prefix") }
      val group = groupPrefix + "." + topic
      props.put("group.id", group)
      props
    }
    new ConsumerConfig(properties)
  }

  private var currentConnection: Option[ConsumerConnector] = None

  def connection: ConsumerConnector = synchronized {
    currentConnection.getOrElse{
      log.info("connecting to topc: $topic")
      val connected = connect()
      currentConnection = Some(connected)
      connected
    }
  }

  /** return an observable stream of decoded data from the kafka topic */
  def stream()(implicit execution: ExecutionContext): Observable[T] = {
    iterableStream().toObservable
  }

  /** return an iterator of decoded data from the kafka topic */
  def iterableStream(): Iterator[T] = {
    val iterator = rawIterator() // on timeout we can just reuse this iterator.. Kafka's iterators are oddly reusable

    RecoverableIterator(() => iterator) {
      case timeout: ConsumerTimeoutException =>
        log.info(s"kafka consumer timeout: on topic $topic")
    }
  }

  /** Store the current reader position in zookeeper.  On restart (e.g. after a crash),
    * the reader will begin at the stored position for this topic and consumerGroup.
    */
  def commit(): Unit = {
    connection.commitOffsets // KAFKA should have () on this side-effecting function
  }

  /** Close the connection, allowing another reader in the same consumerGroup to take
    * over reading from this topic/partition.
    */
  def close(): Unit = {
    connection.shutdown()
  }

  /** open a connection to kafka */
  private def connect(): ConsumerConnector = {
    Consumer.create(consumerConfig)
  }

  /** shutdown current connection and reconnect */
  private def reconnect(): ConsumerConnector = synchronized {
    currentConnection.map(_.shutdown())
    currentConnection = None
    connection
  }

  /** Return an iterator over the incoming kafka data.
   *
   *  For now, we only create one raw iterator per reader, but in the future, we may
   *  want to recreate iterators after some kafka errors. */
  private def rawIterator(): Iterator[T] = {
    val decoder = implicitly[Decoder[T]]
    val topicFilter = new Whitelist(topic)
    val streams = connection.createMessageStreamsByFilter(topicFilter, 1, StringDecoder, decoder)
    val stream: KafkaStream[String, T] = streams.head

    val iter = stream.iterator()
    val messages = iter.map { messageAndMetadata =>
      messageAndMetadata.message
    }
    messages
  }

}

/** Enables reading streams from kafka topics */
object KafkaReader {
  def apply[T: Decoder](topic: String, config: Config = ConfigFactory.load(), clientGroup: Option[String]): KafkaReader[T] =
    new KafkaReader[T](topic, config, clientGroup)
}
