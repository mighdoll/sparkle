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
import nest.sparkle.util.RandomUtil.randomAlphaNum
import scala.concurrent.ExecutionContext
import nest.sparkle.loader.kafka.KafkaEncoders.Implicits._
import nest.sparkle.loader.kafka.KafkaDecoders.Implicits._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import nest.sparkle.util.Log
  

/** Create a kafka reader/writer pair for a kafka test.  
 *  Note topics are not cleaned up: use withKafkaTestTopic instead if possible.  */
private class KafkaTestTopic(rootConfig:Config, id:String = randomAlphaNum(3)) extends Log {
  val topic = s"testTopic-$id"
  val clientGroup = s"testClient-$id"

  val writer = KafkaWriter[String](topic, rootConfig)
  val reader = KafkaReader[String](topic, rootConfig, clientGroup = Some(clientGroup))
  
  log.debug("created test topic {}", topic)
  
  def close() {
    writer.close()
    reader.close()
    log.debug(s"close test topic {}", topic)
  }
}

/** run a test with a kafka reader/writer pair, cleaning up afterwards */
object KafkaTestTopic {
  
  /** run a test with a kafka reader/writer pair, cleaning up afterwards */
  def withKafkaTestTopic[T](rootConfig:Config, id:String = randomAlphaNum(3))(fn:KafkaTestTopic => T):T = {
    var topic:KafkaTestTopic = null
    try {
      topic = new KafkaTestTopic(rootConfig, id)
      fn(topic)
    } finally {
      topic.close()
    }
  }
}
