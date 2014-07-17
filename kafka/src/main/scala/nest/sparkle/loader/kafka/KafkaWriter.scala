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

import kafka.producer.ProducerConfig
import kafka.javaapi.producer.Producer
import kafka.producer.KeyedMessage
import kafka.serializer.Encoder
import rx.lang.scala.Observable
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import nest.sparkle.util.ConfigUtil
import nest.sparkle.util.Log

/** enables writing to a kafka topic */
class KafkaWriter[T: Encoder](topic: String, rootConfig: Config) extends Log{
  private val writer = implicitly[Encoder[T]]

  private lazy val producer: Producer[String, Array[Byte]] = {
    val properties = ConfigUtil.properties(rootConfig.getConfig("sparkle-time-server.kafka-loader.kafka-writer"))
    new Producer[String, Array[Byte]](new ProducerConfig(properties))
  }

  /** write an Observable stream to kafka.  */
  def writeStream(stream: Observable[T]): Unit = {
    stream.subscribe { datum =>
      writeElement(datum)
    }
  }

  /** write a collection of items to kafka. Note that this blocks the calling thread until it is done. */
  def write(data: Iterable[T]): Unit = {
    data foreach writeElement
  }

  /** close the underlying kafka producer connection */
  def close(): Unit = {
    producer.close  // KAFKA should have parens on close
  }

  /** write a single item to a kafka topic. */
  private def writeElement(item: T): Unit = {
    log.trace(s"writing $item to topic: $topic")
    val encoded = writer.toBytes(item)
    val message = new KeyedMessage[String, Array[Byte]](topic, encoded)
    producer.send(message)
  }

}

/** enables writing to a kafka topic */
object KafkaWriter {
  def apply[T: Encoder](topic: String, config: Config = ConfigFactory.load()): KafkaWriter[T] =
    new KafkaWriter[T](topic, config)
}
