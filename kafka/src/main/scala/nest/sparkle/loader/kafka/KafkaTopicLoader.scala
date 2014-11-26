package nest.sparkle.loader.kafka

import java.util.concurrent.{TimeUnit, TimeoutException}

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.{Await, Future, ExecutionContext, promise}
import scala.concurrent.duration._
import scala.compat.Platform.currentTime
import scala.reflect.runtime.universe._
import scala.language.existentials
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

import com.typesafe.config.Config

import kafka.consumer.ConsumerTimeoutException

import nest.sparkle.loader._
import nest.sparkle.loader.Loader.{Events, LoadingTransformer, TaggedBlock}
import nest.sparkle.store.{Event, WriteableStore}
import nest.sparkle.store.cassandra.{CanSerialize, RecoverCanSerialize}
import nest.sparkle.util.{Instrumented, Log, Instance, ConfigUtil, Watched}
import nest.sparkle.util.KindCast.castKind
import nest.sparkle.util.TryToFuture.FutureTry

import KafkaTopicLoader._

/**
 * A runnable that reads a topic from a KafkaReader that returns messages containing Avro encoded
 * records that contains an array of values and writes the values to the Sparkle store.
 */
class KafkaTopicLoader[K: TypeTag]( val rootConfig: Config,
                                    val store: WriteableStore,
                                    val topic: String,
                                    val decoder: KafkaKeyValues
) (implicit execution: ExecutionContext)
  extends Watched[ColumnUpdate[K]]
  with Runnable
  with Instrumented
  with Log
{  
  private val loaderConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("kafka-loader")

  /** Evidence for key serializing when writing to the store */
  private implicit val keySerialize = RecoverCanSerialize.tryCanSerialize[K](typeTag[K]).get
  
  val reader = KafkaReader(topic, rootConfig, None)(decoder)
  
  val transformer = makeTransformer()
  
  /** Current Kafka iterator */
  private var currentIterator: Option[Iterator[Try[TaggedBlock]]] = None
  
  /** Commit interval millis for compare */
  val commitTime = loaderConfig.getDuration("commit-interval", TimeUnit.MILLISECONDS)
  
  /** ms to wait between the first write failure and the first retry. */
  val InitialWriteSleepTime = 10L
  
  /** Number of events to write to the log when tracing writes */
  val NumberOfEventsToTrace = 3
  
  /** timestamp when the last Kafka offsets commit was done */
  private var lastCommit = currentTime

  /** Set to false to shutdown in an orderly manner */
  @volatile
  private var keepRunning = true
  
  /** Promise backing shutdown future */
  private val shutdownPromise = promise[Unit]()
  
  /** Allows any other code to take action when this loader terminates */
  val shutdownFuture = shutdownPromise.future
  
  // Metrics
  private val metricPrefix = topic.replace(".", "_").replace("*", "") 
  
  /** Record convert timer */
  private val convertMetric = metrics.timer("kafka-message-convert", metricPrefix)
  
  /** Read rate */
  private val readMetric = metrics.meter("kafka-messages-read", metricPrefix)
  
  /** Meter for writing to C* for this topic */
  private val writeMetric = metrics.timer("store-writes", metricPrefix) // TODO: make histogram
  
  /** Errors writing to C* for this topic */
  private val writeErrorsMetric = metrics.meter("store-write-errors", metricPrefix)

  /**
   * Main method of the loader.
   * 
   * Reads from a Kafka iterator and writer to the store.
   */
  override def run(): Unit = {
    
    try {
      log.info("Loader for {} started", topic)

      while (keepRunning) {
        conditionalCommit()

        iterator.next() match {
          case Success(block)                         =>
            readMetric.mark()
            blockingWrite(block)
          case Failure(err: ConsumerTimeoutException) =>
            log.trace("consumer timeout reading {}", topic)
          case Failure(err)                           =>
            // Some other Kafka reading error. Discard iterator and try again.
            discardIterator()
        }
      }

      log.info(s"$topic loader is terminating")

      reader.commit() // commit any outstanding offsets
      reader.close()

      log.info(s"$topic loader has terminated")
    } finally {
      shutdownPromise.success()
    }
  }
  
  /** Shutdown this loader nicely */
  def shutdown(): Future[Unit] = {
    keepRunning = false
    shutdownFuture  // this is public but convenient to return to caller
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
   * Return the current iterator if it exists or create a new one and return it.
   * 
   * Note that this method will block the current thread until a new iterator can be obtained.
   * This will happen if Kafka or Zookeeper are down or failing.
   * 
   * @return iterator
   */
  private def iterator: Iterator[Try[TaggedBlock]] = {
    currentIterator.getOrElse {
      val kafkaIterator = KafkaIterator[ArrayRecordColumns](reader)(decoder)
      
      val decodeIterator = kafkaIterator map { tryMessageAndMetadata =>
        tryMessageAndMetadata flatMap { messageAndMetadata =>
          KeyValueColumnConverter.convertMessage(decoder, messageAndMetadata.message())
        }
      }
      
      // Use transformer if one exists
      val iter = transformer map { _ =>
        decodeIterator map {tryBlock => 
          tryBlock flatMap {block => transform(block)}
        }
      } getOrElse decodeIterator
      
      currentIterator = Some(iter)
      iter
    }
  }
  
  private def discardIterator(): Unit = {
    currentIterator = None
    reader.close()  // Ensure Kafka connection is closed.
  }
  
  /** Commit the topic offsets if enough time has passed */
  private def conditionalCommit(): Unit = {
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
  }
  
  /** Transform the block. Only called if transformer is not None */
  private def transform(block: TaggedBlock): Try[TaggedBlock] = {
    try {
      Success(transformer.get.transform(block))
    } catch {
      case NonFatal(err) => Failure(err)
    }
  }

  /** Write the block to the Store. This method blocks the current thread. 
    * 
    * @param block TaggedBlock from a Kafka message.
    */
  private def blockingWrite(block: TaggedBlock): Unit = {
    writeMetric.time {
      var writeComplete = false
      var sleepTime = InitialWriteSleepTime
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
        def withFixedType[U](): Future[Option[ColumnUpdate[K]]] = {
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
          log.warn(s"no last key  for $columnPath. events empty: ${events.isEmpty}")
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

object KafkaTopicLoader
{
  /** Maximum time to wait before retrying after Store write failure */
  val maxStoreRetryWait = 60000L
}
