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
import scala.collection.JavaConverters._
import kafka.consumer.Whitelist
import nest.sparkle.util.Instance
import scala.concurrent.ExecutionContext
import nest.sparkle.util.Log
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.store.Event
import org.apache.avro.generic.GenericRecord
import nest.sparkle.store.WriteableStore

/** Start a stream loader that will load data from kafka topics into cassandra */
class AvroKafkaLoader(config: Config, storage: WriteableStore)(implicit execution: ExecutionContext) extends Log {
  val topics = config.getStringList("topics").asScala
  val schemaFinderClassName = config.getString("schema-finder")
  val schemaFinder = Instance.byName[SchemaFinder](schemaFinderClassName)()

  case class ReaderWithParseInfo(reader: KafkaReader[GenericRecord], parseInfo: SchemaParseInfo)
  val readers = topics.map { topic =>
    val schemaAndInfo = schemaFinder.schemaFor(topic)
    val decoder = AvroSupport.genericDecoder(schemaAndInfo.schema)
    val reader = KafkaReader(topic, config, None)(decoder)
    ReaderWithParseInfo(reader, schemaAndInfo.parseInfo)
  }

  readers.foreach {
    case ReaderWithParseInfo(reader, parseInfo) =>
      val stream = reader.stream()
      val columnName = parseInfo.name
      val columnPrefix = "servers/" // TODO parameterize me
      stream.subscribe { record =>
        val id = record.get(parseInfo.idField)
        val columnPath = s"$columnPrefix$id/$columnName"
        // TODO handle array kafka records with contained arrays
        storage.writeableColumn[Long, Double](columnPath).foreach { column =>
          val key = record.get(parseInfo.keyField).asInstanceOf[Long] // LATER make this type flexible
          parseInfo.valueFields.foreach { valueField =>
            val value = record.get(valueField).asInstanceOf[Double] // LATER make this type flexible.
            val event = Event(key, value)
            column.write(List(event))
          }
        }
      }
  }

}


/*
A note about kafka topics and connections:

It would be nice to start streams from multiple topics with one of the connection.createMessageStreams()
variants. This has the advantage of minimizing the amount of rebalancing when a node is added or restarted, 
since rumor has it that one multi-topic request will trigger one total rebalancing rather than one rebalancing 
for each topic.

However, the connection.commitOffset call tracks topic offsets for the entire connection's worth of 
streams, not per stream.  Using multiple streams per connection and commitOffset would introduce potential 
correctness problems - the topic offsets for some streams might be recorded before the app has committed 
the topic data to cassandra, resulting in data loss if a node fails.

So given a choice between a performance degradation on node failure vs. correctness violation, we'll
go with the performance degradation. 

One topic per connection.

*/
