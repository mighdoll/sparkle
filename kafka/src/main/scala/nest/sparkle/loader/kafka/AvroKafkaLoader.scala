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

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.{ExecutionContext, Future}
import scala.language.existentials
import scala.reflect.runtime.universe._
import scala.util.matching.Regex

import rx.lang.scala.{Observable, Observer, Subject}
import com.typesafe.config.Config

import nest.sparkle.loader.ColumnUpdate
import nest.sparkle.loader.Loader.{Events, LoadingFilter, TaggedBlock}
import nest.sparkle.loader.kafka.TypeTagUtil.typeTaggedToString
import nest.sparkle.store.cassandra.{CanSerialize, RecoverCanSerialize}
import nest.sparkle.store.{Event, WriteableStore}
import nest.sparkle.util.Exceptions.NYI
import nest.sparkle.util.KindCast.castKind
import nest.sparkle.util.TryToFuture.FutureTry
import nest.sparkle.util.{Instance, Log, Watched, Instrumented, ConfigUtil}


object AvroKafkaLoader {
  /** thrown if a nullable id field is null and no default value was defined */
  case class NullableFieldWithNoDefault(msg: String) // format: OFF
     extends RuntimeException(s"nullable id field is null and no default value was defined: $msg") // format: ON
  
  private case class ReaderDecoder[T](reader: KafkaReader[T], decoder: KafkaKeyValues)
}
import nest.sparkle.loader.kafka.AvroKafkaLoader._

/** Start a stream loader that will load data from kafka topics into cassandra
  * type parameter K is the type of the key in the store, (which is not necessarily the same as the type
  * of keys in the kafka stream.)
  */
class AvroKafkaLoader[K: TypeTag](rootConfig: Config, storage: WriteableStore) // format: OFF
    (implicit execution: ExecutionContext) 
  extends Watched[ColumnUpdate[K]] 
  with Instrumented
  with Log 
{ // format: ON

  private val loaderConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("kafka-loader")

  private implicit val keySerialize = RecoverCanSerialize.tryCanSerialize[K](typeTag[K]).get

  private lazy val filters = makeFilters()
  
  // TODO: Make this a Histogram
  private val writeErrorsMetric = metrics.meter("store-write-errors")

  /** list of topic names we'll read from kafka, mapped to the kafka readers
    * who will read the kafka topics
    */
  private lazy val topicReaders = {
    val topics = loaderConfig.getStringList("topics").asScala.toSeq
    val finder = decoderFinder()
    topics.map { topic =>
      val readerDecoder = {
        val decoder = columnDecoder(finder, topic)
        val reader = KafkaReader(topic, rootConfig, None)(decoder)
        ReaderDecoder(reader, decoder)
      }
      topic -> readerDecoder
    }.toMap
  }

  if (loaderConfig.getBoolean("auto-start")) {
    start()
  }

  /** Start the loader reading from the configured kafka topics
    *
    * Note that start() will consume a thread for each kafka topic
    * (there is no async api in kafka 0.8.1).
    */
  def start(): Unit = {
    topicReaders.foreach {
      case (topic, ReaderDecoder(reader, decoder)) =>
        loadKeyValues(topic, reader, decoder)
    }
  }

  /** terminate all readers */
  def close(): Unit = {
    topicReaders.foreach { case (topic, ReaderDecoder(reader, decoder)) => reader.close() }
  }

  /** Load from a single topic via a KafkaReader. Extract Events from each kafka record, and
    * write the events to storage.
    */
  private def loadKeyValues(topic: String, reader: KafkaReader[ArrayRecordColumns],
                            decoder: KafkaKeyValues): Unit = {
    val sourceBlocks = readSourceBlocks(reader, decoder)
    val output = outputPipeline(topic, reader)

    log.debug(s"starting load on topic: $topic")
    // connecting the output to the source triggers the data to start flowing
    sourceBlocks.subscribe(output)
  }

  /** return an Observer that will filter and write data to storage. The action here is lazy - 
   *  nothing flows through pipeline until someone starts pushing data into the returned Observer. 
   */
  private def outputPipeline(topic: String, reader: KafkaReader[_]): Observer[TaggedBlock] = {
    val sourceSubject = Subject[TaggedBlock]()

    val filtered = attachFilter(topic, sourceSubject)
    val written = attachWriter(filtered, reader)
    written.subscribe() // connect writer and filter to our source 
    
    sourceSubject  
  }

  /** construct a map of filters to apply to data read from kafka source topics.
    * The keys are regexes that match topic names, and the
    */
  private def makeFilters(): Map[Regex, LoadingFilter] = {
    val filterList = loaderConfig.getConfigList("filters").asScala.toSeq
    val entries = filterList.map { configEntry =>
      val matcher = configEntry.getString("match")
      val filterClass = configEntry.getString("filter")

      val filter: LoadingFilter = Instance.byName(filterClass)(rootConfig)
      matcher.r -> filter
    }
    entries.toMap
  }

  private def matching(regex: Regex, toMatch: String): Boolean = {
    regex.unapplySeq(toMatch).isDefined
  }

  /** If a filter is defined in the .conf file for this topic, attach the filter to a source Observable.
   *  Return an Observable of the filtered results. */
  private def attachFilter(topic: String, source: Observable[TaggedBlock]): Observable[TaggedBlock] = {
    val foundFilter = filters.collectFirst {
      case (regex, filter) if matching(regex, topic) => filter
    }

    foundFilter.map { filter =>
      log.debug(s"attaching filter: $filter to topic: $topic")
      filter.filter(source)
    }.getOrElse {
      source
    }
  }

  /** Attach a storage writing stage to an Observable pipeline of TaggedBlocks. Return an Observable with
   *  the storage writing stage attached. */
  private def attachWriter[T](pipeline: Observable[TaggedBlock], reader: KafkaReader[T]): Observable[TaggedBlock] = {
    pipeline.map { block =>
      val written = writeBlock(block)
      written.map { updates =>
        recordComplete(reader, updates)
      }
      block
    }
  }

  /** Return an observable that reads blocks of data from kafka.
    * Reading begins when the caller subscribes to the returned Observable.
    */
  private def readSourceBlocks(reader: KafkaReader[ArrayRecordColumns], // format: OFF
                               decoder: KafkaKeyValues): Observable[TaggedBlock] = { // format: ON

    val stream = 
      reader.stream().doOnError { err =>
        log.error(s"Error reading Kafka stream for ${reader.topic}", err)
      }

    val sourceBlocks: Observable[TaggedBlock] =
      stream.map { record =>
        val columnPathIds = {
          val ids = decoder.metaData.ids zip record.ids map {
            case (NameTypeDefault(name, typed, default), valueOpt) =>
              val valueOrDefault = valueOpt orElse default orElse {
                throw NullableFieldWithNoDefault(name)
              }
              typeTaggedToString(valueOrDefault.get, typed)
          }
          ids.foldLeft("")(_ + "/" + _).stripPrefix("/")
        }

        val block =
          record.typedColumns(decoder.metaData).map { taggedColumn =>
            val columnPath = decoder.columnPath(columnPathIds, taggedColumn.name)

            /** do the following with type parameters matching each other
              * (even though our caller will ultimately ignore them)
              */
            def withFixedTypes[T, U]() = {
              val typedEvents = taggedColumn.events.asInstanceOf[Events[T, U]]
              val keyType: TypeTag[T] = castKind(taggedColumn.keyType)
              val valueType: TypeTag[U] = castKind(taggedColumn.valueType)
              TaggedSlice[T, U](columnPath, typedEvents)(keyType, valueType)
            }
            withFixedTypes[Any,Any]()
          }

        log.trace(s"readSourceBlock: got block.length ${block.length}  head:${block.headOption}")
        block
      }

    sourceBlocks
  }

  /** Write chunks of column data to the store. Return a future that completes when the data has been written. */
  private def writeBlock(taggedBlock: TaggedBlock)(implicit keyType: TypeTag[K]): Future[Seq[ColumnUpdate[K]]] = {
    val writeFutures =
      taggedBlock.map { slice =>
        def withFixedType[U]() = {
          implicit val valueType = slice.valueType
          log.info(s"loading ${slice.events.length} events to column: ${slice.columnPath}"
            + s"  keyType: $keyType  valueType: $valueType")

          val result =
            for {
              valueSerialize <- RecoverCanSerialize.tryCanSerialize[U](valueType).toFuture
              castEvents:Events[K,U] = castKind(slice.events)
              update <- writeEvents(castEvents, slice.columnPath)(valueSerialize)
            } yield {
              update
            }

          result.failed.map { err => 
            log.error("writeBlocks failed", err) 
            writeErrorsMetric.mark()
          }
          result
        }
        withFixedType()
      }

    val allDone: Future[Seq[ColumnUpdate[K]]] =
      Future.sequence(writeFutures).map { updates =>
        val flattened = updates.flatten // remove Nones
        flattened
      }

    allDone
  }

  /** We have written one record's worth of data to storage. Per the batch policy, commit our
    * position in kafka and notify any watchers.
    */
  private def recordComplete(reader: KafkaReader[_], updates: Seq[ColumnUpdate[K]]): Unit = {
    // TODO, commit is relatively slow. let's commit/notify only every N items and/or after a timeout
    //reader.commit() // record the progress reading this kafka topic into zookeeper

    // notify anyone subscribed to the Watched stream that we've written some data
    updates.foreach { update =>
      log.trace(s"recordComplete: $update")
      watchedData.onNext(update)
    }
  }

  /** write some typed Events to storage. return a future when that completes when the writing is done*/
  private def writeEvents[U: CanSerialize](events: Iterable[Event[K, U]],
                                           columnPath: String): Future[Option[ColumnUpdate[K]]] = {
    storage.writeableColumn[K, U](columnPath) flatMap { column =>
      events.lastOption.map { _.argument } match {
        case Some(lastKey) =>
          column.write(events).map { _ =>
            Some(ColumnUpdate[K](columnPath, lastKey))
            // TODO: Add metric for writing to this columnPath
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
    val className = loaderConfig.getString("find-decoder")
    Instance.byName[FindDecoder](className)(rootConfig)
  }

  /** return the kafka decoder for a given kafka topic */
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
