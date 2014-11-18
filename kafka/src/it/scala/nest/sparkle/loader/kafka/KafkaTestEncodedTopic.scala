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
import nest.sparkle.loader.SparkleSerializer
import nest.sparkle.util.RandomUtil.randomAlphaNum
import kafka.serializer.{Encoder, Decoder}

/** test utility class for reading/writing type Ts from/to a kafka topic **/
class KafkaTestEncodedTopic[T](val rootConfig: Config, val serde: SparkleSerializer[T], val testId: String) {
  val topic = s"testTopic-$testId"  // TODO DRY with testTopicName

  val encoder =  new Encoder[T] {
    def toBytes(data: T) : Array[Byte] = {
      serde.toBytes(data)
    }
  }

  val writer = KafkaWriter(topic, rootConfig)(encoder)
}

object KafkaTestUtil {

  val stringSerde = new SparkleSerializer[String] {
    def toBytes(data: String): Array[Byte] = {
      data.getBytes("UTF-8")
    }

    def fromBytes(bytes: Array[Byte]): String = {
      new String(bytes, "UTF-8")
    }
  }

  def withTestEncodedTopic[T, R](rootConfig: Config,
                              serde: SparkleSerializer[T],
                              id: String = randomAlphaNum(3))(fn: KafkaTestEncodedTopic[T] => R): R = {
    val kafka = new KafkaTestEncodedTopic(rootConfig, serde, id)
    try {
      fn(kafka)
    } finally {
      // TODO delete topic when we switch to KAFKA 0.8.1+
    }
  }

  def withTestReader[T, R](testTopic: KafkaTestEncodedTopic[T], serde: SparkleSerializer[T])(fn: KafkaReader[T] => R): R = {
    val clientGroup = s"testClient-${testTopic.testId}"
    val decoder = new Decoder[T] {
      def fromBytes(bytes: Array[Byte]) : T = {
        serde.fromBytes(bytes)
      }
    }
    val reader = KafkaReader(testTopic.topic, testTopic.rootConfig, clientGroup = Some(clientGroup))(decoder)
    try {
      fn(reader)
    } finally {
      reader.close()
    }
  }
  
  def testTopicName(schemaName:String = "test", id: String = randomAlphaNum(3)):String = {
    s"$schemaName.testTopic-$id"
  }
  
}
