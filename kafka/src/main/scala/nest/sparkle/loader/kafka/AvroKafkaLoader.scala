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

import java.util.concurrent.Executors

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.ExecutionContext
import scala.language.existentials
import scala.reflect.runtime.universe._

import com.typesafe.config.Config

import nest.sparkle.store.cassandra.RecoverCanSerialize
import nest.sparkle.store.WriteableStore
import nest.sparkle.util.{Log, ConfigUtil}

/** Start stream loaders that will load data from kafka topics into cassandra
  * type parameter K is the type of the key in the store, (which is not necessarily the same as the type
  * of keys in the kafka stream.)
  */
class AvroKafkaLoader[K: TypeTag](rootConfig: Config, storage: WriteableStore) // format: OFF
    (implicit execution: ExecutionContext) 
  extends Log 
{ // format: ON
  private val loaderConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("kafka-loader")
  private lazy val topics = loaderConfig.getStringList("topics").asScala.toSeq
  
  // Each KafkaReader consumes a thread for the underlying Kafka Consumer stream iterator
  private lazy val readerThreadPool = Executors.newFixedThreadPool(topics.size)
  private lazy val readerExecutionContext = ExecutionContext.fromExecutor(readerThreadPool)

  //private implicit val keySerialize = RecoverCanSerialize.tryCanSerialize[K](typeTag[K]).get

  private val loaders = topics.map(new KafkaAvroArrayTopicLoader[K](rootConfig, storage, _)).toList

  /** Start the loader for topic.
    *
    * Note that start() will consume a thread for each kafka topic
    * (there is no async api in kafka 0.8.1).
    */
  def start(): Unit = {
    loaders foreach { loader =>
      readerExecutionContext.execute(loader)
    }
  }
  
  /** terminate all loaders */
  def shutdown(): Unit = {
    loaders.foreach(_.shutdown())
  }
}
