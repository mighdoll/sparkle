package nest.sparkle.loader.kafka

import java.util.concurrent.TimeoutException

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.{Await, Future, ExecutionContext}
import scala.concurrent.duration._
import scala.compat.Platform.currentTime
import scala.reflect.runtime.universe._
import scala.language.existentials
import scala.util.{Success, Failure}
import scala.util.control.NonFatal

import com.typesafe.config.Config

import kafka.consumer.ConsumerTimeoutException

import nest.sparkle.loader.ColumnUpdate
import nest.sparkle.loader.Loader.{Events, LoadingTransformer, TaggedBlock}
import nest.sparkle.store.{Event, WriteableStore}
import nest.sparkle.store.cassandra.{CanSerialize, RecoverCanSerialize}
import nest.sparkle.util.{Instrumented, Log, Instance, ConfigUtil, Watched}
import nest.sparkle.util.Exceptions.NYI
import nest.sparkle.util.KindCast.castKind
import nest.sparkle.util.TryToFuture.FutureTry

import KafkaAvroArrayTopicLoader._

/**
 * A runnable that reads a topic from a KafkaReader that returns messages containing Avro encoded
 * records that contains an array of values and writes the values to the Sparkle store.
 */
class KafkaAvroArrayTopicLoader[K: TypeTag]( 
    val rootConfig: Config, val store: WriteableStore, val topic: String
  ) (implicit execution: ExecutionContext) 
  extends Watched[ColumnUpdate[K]]
  with Runnable
  with Instrumented
  with Log
{
  private val loaderConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("kafka-loader")

  /** ??? */
  private implicit val keySerialize = RecoverCanSerialize.tryCanSerialize[K](typeTag[K]).get
  
  val finder  = decoderFinder()
  val decoder = columnDecoder(finder, topic)
  val reader  = KafkaReader(topic, rootConfig, None)(decoder)
  
  val transformer = makeTransformer()
  
  /** Current Kafka iterator */
  private var currentIterator: Option[TaggedBlockIterator] = None
  
  /** Convert commit interval seconds to millis for compare */
  val commitTime = {
    val commitIntervalSeconds = loaderConfig.getInt("commit-interval")
    commitIntervalSeconds * 1000L
  }

  /** Set to false to shutdown in an orderly manner */
  private var keepRunning = true
  
  /** Meter for writing to C* for this topic */
  private val writeMetric = metrics.timer(s"store-writes-$topic") // TODO: make histogram
  
  /** Errors writing to C* for this topic */
  private val writeErrorsMetric = metrics.meter(s"store-write-errors-$topic")

  /**
   * Main method of the loader.
   * 
   * Reads from a Kafka iterator and writer to the store.
   */
  override def run(): Unit = {
    
    log.info("Loader for {} started", topic)

    var lastCommit = currentTime
    while (keepRunning) {
      // Commit every N seconds
      val now = currentTime
      if (now - lastCommit >= commitTime) {
        try {
          reader.commit()
          lastCommit = now
        } catch {
          case NonFatal(err) =>
            log.error(s"Unhandled exception committing kafka offsets for $topic", err)
            // State of kafka connection is unknown. Discard iterator, new one will be created
            discardIterator()
        }
      }

      iterator.next() match {
        case Success(block)                         => blockingWrite(block)
        case Failure(err: ConsumerTimeoutException) => log.trace("consumer timeout reading {}", topic)
        case Failure(err)                           => 
          // Some other Kafka reading error. Discard iterator and try again.
          discardIterator()
      }
    }

    log.info(s"$topic loader is terminating")
    
    reader.commit()  // commit any outstanding offsets
    reader.close()
    
    log.info(s"$topic loader has terminated")
  }
  
  /** Shutdown this loader nicely */
  def shutdown(): Unit = {
    keepRunning = false
  }

  /** Create the transformer, if any, for this topic.
    * The first pattern to match is used.
    */
  private def makeTransformer(): Option[LoadingTransformer] = {
    val transformerList = loaderConfig.getConfigList("transformers").asScala.toSeq
    val transformerConfig = transformerList find { configEntry =>
      val regex = configEntry.getString("match").r
      regex.pattern.matcher(topic).matches()
    }
    
    transformerConfig.map { configEntry =>
      val className = configEntry.getString("transformer")
      val transformer: LoadingTransformer = Instance.byName(className)(rootConfig)
      transformer
    }
  }

  /**
   * Create an Iterator that returns a Try where a Success is the next item and
   * Failure is an exception.
   * 
   * The main use for this is to return ConsumerTimeoutExceptions to the user so it can
   * perform commits or other processing.
   * 
   * This also allows handling other exceptions which may require creating a new iterator to
   * recover.
   * 
   * @return iterator
   */
  private def iterator: TaggedBlockIterator = {
    currentIterator.getOrElse {
      val kafkaIterator = KafkaIterator(reader)
      val decodeIterator = DecodeIterator(kafkaIterator, decoder)
      val iter = transformer.map(TransformIterator(decodeIterator,_)).getOrElse(decodeIterator)
      currentIterator = Some(iter)
      currentIterator.get
    }
  }
  
  private def discardIterator() {
    currentIterator = None
    reader.close()
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

  /** Write the block to the Store. This method blocks the current thread. 
    * 
    * @param block TaggedBlock from a Kafka message.
    */
  private def blockingWrite(block: TaggedBlock) {
    writeMetric.time {
      var writeComplete = false
      var sleepTime = 10L
      while (!writeComplete) {
        val writeFuture = writeBlock(block)
        try {
          // Block on writing.
          Await.ready(writeFuture, 60 seconds)
          writeFuture.value.map { 
            case Success(updates) =>
              recordComplete(updates)
              writeComplete = true
            case Failure(err)     =>
              log.error(s"Writes for $topic failed, retrying", err)
              // Should check the err and see if it's dependent on the data or not.
              // Sleep with limited back-off
              Thread.sleep(sleepTime)
              sleepTime = {
                sleepTime match {
                  case t if t >= maxStoreRetryWait => maxStoreRetryWait
                  case _                           => sleepTime * 2L
                }
              }
          }
        } catch {
          case e: TimeoutException =>
            log.warn(s"Write for $topic timed out. Will retry...")
        }
      }
    }
  }

  /** Write chunks of column data to the store. Return a future that completes when the data has been written. */
  private def writeBlock(taggedBlock: TaggedBlock)(implicit keyType: TypeTag[K]): Future[Seq[ColumnUpdate[K]]] = {
    val writeFutures =
      taggedBlock.map { slice => 
        def withFixedType[U]() = {
          implicit val valueType = slice.valueType
          log.debug(
            "loading {} events to column: {}  keyType: {}  valueType: {}",
            slice.events.length.toString, slice.columnPath, keyType, valueType
          )

          val result =
            for {
              valueSerialize <- RecoverCanSerialize.tryCanSerialize[U](valueType).toFuture
              castEvents: Events[K, U] = castKind(slice.events)
              update <- writeEvents(castEvents, slice.columnPath)(valueSerialize)
            } yield {
              update
            }

          result.failed.foreach { err =>
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

  /** write some typed Events to storage. return a future when that completes when the writing is done */
  private def writeEvents[U: CanSerialize](events: Iterable[Event[K, U]],
                                           columnPath: String): Future[Option[ColumnUpdate[K]]] = {
    store.writeableColumn[K, U](columnPath) flatMap { column =>
      events.lastOption.map {_.argument} match {
        case Some(lastKey) =>
          column.write(events).map { _ => 
            Some(ColumnUpdate[K](columnPath, lastKey))
          }
        case None          =>
          log.error(s"what does this mean for this $columnPath?")
          Future.successful(None)
      }
    }
  }

  /** We have written one record's worth of data to storage. Per the batch policy
    * notify any watchers.
    */
  private def recordComplete(updates: Seq[ColumnUpdate[K]]): Unit = {
    // notify anyone subscribed to the Watched stream that we've written some data
    try {
      updates.foreach { update =>
        log.trace(s"recordComplete: $update")
        watchedData.onNext(update)
      }
    } catch {
      case NonFatal(err)  =>
        // Just log error and ignore
        log.warn(s"Exception notifying for $topic", err)
    }
  }

}

object KafkaAvroArrayTopicLoader
{
  /** Maximum time to wait before retrying after Store write failure */
  val maxStoreRetryWait = 60000L
}
