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

import java.util.concurrent.TimeUnit

import rx.lang.scala.Observable

import com.typesafe.config.{Config, ConfigFactory}

import kafka.common.FailedToSendMessageException
import kafka.javaapi.producer.Producer
import kafka.producer.{KeyedMessage, ProducerConfig}
import kafka.serializer.Encoder

import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.{ConfigUtil, Log}

/** enables writing to a kafka topic */
class KafkaWriter[T: Encoder](topic: String, rootConfig: Config) extends Log
{
  private val writer = implicitly[Encoder[T]]
  
  private lazy val writerConfig = rootConfig.getConfig(s"$sparkleConfigName.kafka-loader.kafka-writer")
  private lazy val SendRetryWait = writerConfig.getDuration("send-retry-wait", TimeUnit.MILLISECONDS)
  private lazy val SendMaxRetries = writerConfig.getInt("send-max-retries")
  
  private lazy val producer: Producer[String, Array[Byte]] = {
    val properties = ConfigUtil.properties(writerConfig.getConfig("producer"))
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

  /** write a single item to a kafka topic. */
  private def writeElement(item: T): Unit = {
    val encoded = writer.toBytes(item)
    log.trace(s"writing ${encoded.take(8)} length = ${encoded.length}  to topic: $topic")
    val message = new KeyedMessage[String, Array[Byte]](topic, encoded)
      
    var count = SendMaxRetries
    while (true) {
      try {
        producer.send(message)
        return
      } catch {
        case e: FailedToSendMessageException =>
          count -= 1
          if (count == 0) throw e
          log.warn(s"kafka producer send failed ${SendMaxRetries-count} times, retrying")
          Thread.sleep(SendRetryWait)
      }
    }
  }

  /** close the underlying kafka producer connection */
  def close(): Unit = {
    producer.close // KAFKA should have parens on close
  }

}

/** enables writing to a kafka topic */
object KafkaWriter
{
  def apply[T: Encoder](topic: String, config: Config = ConfigFactory.load()): KafkaWriter[T] =
    new KafkaWriter[T](topic, config)
}
