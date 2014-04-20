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

/** create a kafka reader/writer pair for a new test topic.  
 *  Topics are not cleaned up after completion.  */
class KafkaTestTopic(rootConfig:Config, id:String = randomAlphaNum(3)) {
  val topic = s"testTopic-$id"
  val clientGroup = s"testClient-$id"

  val writer = KafkaWriter[String](topic, rootConfig)
  val reader = KafkaReader[String](topic, rootConfig, clientGroup = Some(clientGroup))
}
