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
import nest.sparkle.util.Exceptions.NYI
import nest.sparkle.util.Watched
import kafka.serializer.Decoder
import scala.reflect.runtime.universe._
import scala.language.existentials
import scala.concurrent.Future
import nest.sparkle.store.cassandra.WriteableColumn
import nest.sparkle.store.cassandra.CanSerialize
import nest.sparkle.loader.kafka.TypeTagUtil.typeTaggedToString

/** an update to a watcher about the latest value loaded */
case class ColumnUpdate(columnPath: String, latest: Long)   // LATER make 'latest' type parametric

/** Start a stream loader that will load data from kafka topics into cassandra */
class AvroKafkaLoader(config: Config, storage: WriteableStore) // format: OFF
    (implicit execution: ExecutionContext) extends Watched[ColumnUpdate] with Log { // format: ON
  val topics = config.getStringList("topics").asScala.toSeq

  /** Start the loader reading from the configured kafka topics
    *
    * Note that load() will consume a thread for each kafka
    * topic (there is no async api in kafka 0.8.1).
    */
  def load() {
    val finder = decoderFinder()

    topics.foreach { topic =>
      val decoder = columnDecoder(finder, topic)
      val reader = KafkaReader(topic, config, None)(decoder)
      loadKeyValues(reader, decoder)
    }
  }

  /** Load from a single topic via a KafkaReader. Extract Events from each kafka record, and
    * write the events to storage.
    */
  private def loadKeyValues(reader: KafkaReader[IdKeyAndValues],
                            decoder: KafkaKeyValues) {
    val stream = reader.stream()
    val columnName = decoder.columnName
    val columnPrefix = decoder.dataSetPath
    stream.subscribe { record =>
      val id = typeTaggedToString(record.id, decoder.types.idType)
      val columnPath = s"$columnPrefix$id/$columnName"
      val events = recordToEvents(record, decoder.types)

      val longDoubleEvents = events.collect { case LongDoubleTaggedEvent(event) => event }
      val longLongEvents = events.collect { case LongLongTaggedEvent(event) => event }

      val wroteLongDoubles = writeEvents(longDoubleEvents, columnPath) // SCALA hlist me
      val wroteLongLongs = writeEvents(longLongEvents, columnPath)

      wroteLongDoubles.zip(wroteLongLongs).foreach { _ =>
        reader.commit() // record the progress reading this kafka topic into zookeeper
        notifyWatchers(columnPath, events)
      }
    }
  }

  /** notify anyone subscribed to the Watched stream that we've writtens some data */
  private def notifyWatchers(columnPath: String, events: Iterable[TaggedEvent]) {
    events.lastOption map { last =>
      val update = ColumnUpdate(columnPath, last.event.argument.asInstanceOf[Long])
      watchedData.onNext(update)
    }
  }

  /** write some typed Events to storage. return a future when that completes when the writing is done*/
  private def writeEvents[T: CanSerialize, U: CanSerialize](events: Iterable[Event[T, U]], columnPath: String): Future[Unit] = {
    storage.writeableColumn[T, U](columnPath).flatMap { column =>
      column.write(events)
    }
  }

  /** parse a record into a series of TaggedEvents */
  private def recordToEvents(record: IdKeyAndValues, types: IdKeyAndValuesTypes): Seq[TaggedEvent] = {
    record.keysValues.flatMap {
      case (key, values) =>
        values.zip(types.valueTypes).map{
          case (value, valueType) =>
            val event = Event(key, value)
            TaggedEvent(event, types.keyType, valueType)
        }
    }
  }

  /** instantiate the FindDecoder instance specified in the config file. The FindDecoder
    * is used to map topic names to kafka decoders
    */
  private def decoderFinder(): FindDecoder = {
    val className = config.getString("find-decoder")
    Instance.byName[FindDecoder](className)()
  }

  /** return a kafka topic reader and a kafka decoder based on a given topic */
  private def columnDecoder(finder: FindDecoder, topic: String): KafkaKeyValues = {
    finder.decoderFor(topic) match {
      case keyValueDecoder: KafkaKeyValues => keyValueDecoder
      case _                               => NYI("only KeyValueStreams implemented so far")
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
