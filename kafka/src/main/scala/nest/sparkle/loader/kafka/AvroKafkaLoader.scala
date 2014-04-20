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

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.existentials

import com.typesafe.config.Config

import nest.sparkle.loader.kafka.TypeTagUtil.typeTaggedToString
import nest.sparkle.store.{ Event, WriteableStore }
import nest.sparkle.store.cassandra.CanSerialize
import nest.sparkle.store.cassandra.serializers._
import nest.sparkle.util.{ Instance, Log, Watched }
import nest.sparkle.util.ConfigureLog4j
import nest.sparkle.util.Exceptions.NYI

/** an update to a watcher about the latest value loaded */
case class ColumnUpdate[T](columnPath: String, latest: T)

/** Start a stream loader that will load data from kafka topics into cassandra */
class AvroKafkaLoader[K](rootConfig: Config, storage: WriteableStore) // format: OFF
    (implicit execution: ExecutionContext) extends Watched[ColumnUpdate[K]] with Log { // format: ON

  val config = rootConfig.getConfig("sparkle-time-server.kafka-loader")
  ConfigureLog4j.configure(config)

  val topics = config.getStringList("topics").asScala.toSeq

  if (config.getBoolean("auto-start")) {
    load()
  }

  /** Start the loader reading from the configured kafka topics
    *
    * Note that load() will consume a thread for each kafka topic
    * (there is no async api in kafka 0.8.1).
    */
  def load() {
    val finder = decoderFinder()

    topics.foreach { topic =>
      val decoder = columnDecoder(finder, topic)
      val reader = KafkaReader(topic, rootConfig, None)(decoder)
      loadKeyValues(reader, decoder)
    }
  }

  /** thrown if the avro schema specifies an unimplemented key or value type */
  case class UnsupportedColumnType(msg: String) extends RuntimeException(msg)

  /** Load from a single topic via a KafkaReader. Extract Events from each kafka record, and
    * write the events to storage.
    */
  private def loadKeyValues(reader: KafkaReader[ArrayRecordColumns],
                            decoder: KafkaKeyValues) {
    val stream = reader.stream()
    stream.subscribe { record =>
      val id = typeTaggedToString(record.id, decoder.metaData.idType)

      val writeFutures =
        record.typedColumns(decoder.metaData).map { taggedColumn =>
          val columnPath = decoder.columnPath(id, taggedColumn.name)

          log.info(s"loading ${taggedColumn.events.length} events to column: $columnPath "
            + s"keyType: ${taggedColumn.keyType}  valueType: ${taggedColumn.valueType}")

          taggedColumn match {
            case LongDoubleEvents(events) => writeEvents(events, columnPath)
            case LongLongEvents(events)   => writeEvents(events, columnPath)
            case LongIntEvents(events)    => writeEvents(events, columnPath)
            case LongStringEvents(events) => writeEvents(events, columnPath)
            case _ =>
              val error = s"keyType: ${taggedColumn.keyType}  valueType: ${taggedColumn.valueType}"
              throw UnsupportedColumnType(error)
          }
        }

      // The type parameterization of K is incomplete. we'd need to add a typeclass to 
      // abstract out the LongDouble stuff above.  For now we just cast to the expected type
      val castFutures = writeFutures.asInstanceOf[Seq[Future[Option[ColumnUpdate[K]]]]]

      Future.sequence(castFutures).foreach { updates =>
        val flattened = updates.flatten // remove Nones
        recordComplete(reader, flattened)
      }
    }
  }

  /** We have written one record's worth of data to storage. Per the batch policy, commit our
    * position in kafka and notify any watchers.
    */
  private def recordComplete(reader: KafkaReader[_], updates: Seq[ColumnUpdate[K]]) {
    // TODO, commit is relatively slow. let's commit/notify only every N items and/or after a timeout
    reader.commit() // record the progress reading this kafka topic into zookeeper

    // notify anyone subscribed to the Watched stream that we've written some data 
    updates.foreach { update =>
      watchedData.onNext(update)
    }
  }

  /** write some typed Events to storage. return a future when that completes when the writing is done*/
  private def writeEvents[T: CanSerialize, U: CanSerialize](events: Iterable[Event[T, U]],
                                                            columnPath: String): Future[Option[ColumnUpdate[T]]] = {
    storage.writeableColumn[T, U](columnPath) flatMap { column =>
      events.lastOption.map{ _.argument } match {
        case Some(lastKey) =>
          column.write(events).map { _ =>
            Some(ColumnUpdate[T](columnPath, lastKey))
          }
        case None =>
          Future.successful(None)
      }
    }
  }

  /** instantiate the FindDecoder instance specified in the config file. The FindDecoder
    * is used to map topic names to kafka decoders
    */
  private def decoderFinder(): FindDecoder = {
    val className = config.getString("find-decoder")
    Instance.byName[FindDecoder](className)(rootConfig)
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
