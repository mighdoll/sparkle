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
class KafkaReader[T: Decoder](topic: String, config: Config = ConfigFactory.load(), consumerGroup: Option[String]) {
  private lazy val connection = connect()
  
  /** return a stream for a kafka topic */
  def stream()(implicit execution: ExecutionContext): Observable[T] = {
    val decoder = implicitly[Decoder[T]]
    val topicCountMap = Map(topic -> 1)
    val streamMap = connection.createMessageStreams[String, T](topicCountMap, StringDecoder, decoder)
    val streams = streamMap(topic)
    val stream: KafkaStream[String, T] = streams.head

    val messages = stream.iterator().map { _.message }
    messages.toObservable
  }

  /** Store the current reader position in zookeeper.  On restart (e.g. after a crash),
    * the reader will begin at the stored position for this topic and consumerGroup.
    */
  def commit() {
    connection.commitOffsets  // KAFKA should have () on this side-effecting function
  }

  /** Close the connection, allowing another reader in the same consumerGroup to take 
   *  over reading from this topic/partition.  */
  def close() {
    connection.shutdown()
  }

  /** open a connection to kafka */
  private def connect(): ConsumerConnector = {
    val properties = {
      /** extract the kafka-client settings verbatim, send directly to kafka */
      val props = ConfigUtil.properties(config.getConfig("kafka-reader"))
      val group = consumerGroup.getOrElse { config.getString("consumer-group") }
      props.put("group.id", group)
      props
    }

    val consumerConfig = new ConsumerConfig(properties)
    Consumer.create(consumerConfig)
  }

}

/** Enables reading streams from kafka topics */
object KafkaReader {
  def apply[T: Decoder](topic: String, config: Config = ConfigFactory.load(), clientGroup: Option[String]) =
    new KafkaReader[T](topic, config, clientGroup)
}
