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

class KafkaTestAvroTopic(loaderConfig: Config, id: String = randomAlphaNum(3)) {
  val topic = s"testTopic-$id"
  val clientGroup = s"testClient-$id"
  val schema = AvroSupport.schemaFromString(MillisDoubleAvro.avroJson)

  val decoder = AvroSupport.genericDecoder(schema)
  val encoder = AvroSupport.genericEncoder(schema)
  val writer = KafkaWriter(topic, loaderConfig)(encoder)
  val reader = KafkaReader(topic, loaderConfig, clientGroup = Some(clientGroup))(decoder)
}

object KafkaTestUtil {
  def withTestAvroTopic[T](loaderConfig: Config, id: String = randomAlphaNum(3))(fn: KafkaTestAvroTopic => T): T = {
    val kafka = new KafkaTestAvroTopic(loaderConfig, id)
    try {
      fn(kafka)
    } finally {
      // TODO delete topic when we switch to KAFKA 0.8.1+ 
    }
  }
}
