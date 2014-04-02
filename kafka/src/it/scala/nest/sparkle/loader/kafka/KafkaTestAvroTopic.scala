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
import nest.sparkle.util.RandomUtil.randomAlphaNum
import org.apache.avro.Schema
import kafka.serializer.Decoder
import org.apache.avro.generic.GenericRecord

class KafkaTestAvroTopic(val loaderConfig: Config, val schema: Schema, val testId: String) {
  val topic = s"testTopic-$testId"

  val encoder = AvroSupport.genericEncoder(schema)
  val writer = KafkaWriter(topic, loaderConfig)(encoder)
}

object KafkaTestUtil {
  def withTestAvroTopic[T](loaderConfig: Config,
                           schema: Schema,
                           id: String = randomAlphaNum(3))(fn: KafkaTestAvroTopic => T): T = {
    val kafka = new KafkaTestAvroTopic(loaderConfig, schema, id)
    try {
      fn(kafka)
    } finally {
      // TODO delete topic when we switch to KAFKA 0.8.1+ 
    }
  }

  def withTestReader[T](testTopic: KafkaTestAvroTopic)(fn: KafkaReader[GenericRecord] => T): T = {
    val clientGroup = s"testClient-${testTopic.testId}"
    val decoder = AvroSupport.genericDecoder(testTopic.schema)
    val reader = KafkaReader(testTopic.topic, testTopic.loaderConfig, clientGroup = Some(clientGroup))(decoder)
    try {
      fn(reader)
    } finally {
      reader.close()
    }
  }

}
