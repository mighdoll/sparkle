package nest.sparkle.loader.kafka

import java.util.concurrent.Semaphore

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.{Future, ExecutionContext, promise, Promise}
import scala.reflect.runtime.universe._
import scala.language.existentials
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal
import scala.util.control.Exception.nonFatalCatch
import scala.annotation.tailrec

import com.datastax.spark.connector
import com.datastax.spark.connector.writer
import com.typesafe.config.Config

import kafka.consumer.ConsumerTimeoutException

import nest.sparkle.loader._
import nest.sparkle.loader.Loader.{Events, LoadingTransformer, TaggedBlock}
import nest.sparkle.store.{Event, WriteableStore}
import nest.sparkle.store.cassandra.{WriteableColumn, CanSerialize, RecoverCanSerialize}
import nest.sparkle.util.{Instrumented, Log, Instance, ConfigUtil, Watched}
import nest.sparkle.util.KindCast.castKind
import nest.sparkle.util.TryToFuture.FutureTry

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
  
  /** The number of Kafka messages to process before committing */
  val messageCommitLimit = loaderConfig.getInt("message-commit-limit")
  
  /** The maximum number of concurrent Kafka messages being written simultaneously */
  val maxConcurrentWrites = loaderConfig.getInt("max-concurrent-writes")
  
  /** Number of events to write to the log when tracing writes */
  val NumberOfEventsToTrace = 3

  /** Set to false to shutdown in an orderly manner */
  @volatile
  private var keepRunning = true
  
  /** Promise backing shutdown future */
  private val shutdownPromise = promise[Unit]()
  
  /** Allows any other code to take action when this loader terminates */
  val shutdownFuture = shutdownPromise.future

  /**
   * This limits the number of concurrent C* writes.
   * Fairness is not needed since only the loader thread ever does an acquire.
   */
  private val writesInProcess = new Semaphore(maxConcurrentWrites)
  
  // Metrics
  private val metricPrefix = topic.replace(".", "_").replace("*", "") 
  
  /** Read rate */
  private val readMetric = metrics.meter("kafka-messages-read", metricPrefix)
  
  private val batchSizeMetric = metrics.meter(s"store-batch-size", metricPrefix)
  
  /** Meter for writing to C* for this topic */
  private val writeMetric = metrics.timer("store-writes", metricPrefix) // TODO: make histogram
  
  /** Errors writing to C* for this topic */
  private val writeErrorsMetric = metrics.meter("store-write-errors", metricPrefix)

  /**
   * Main method of the loader.
   * 
   * Reads from a Kafka iterator and writes to the store.
   * 
   * Writes are executed asynchronously until a limit of messages have not been processed.
   * When the limit is hit the thread waits until one of the previous messages is finished.
   * 
   * After C number of messages are processed the thread waits until all the messages data has
   * been written to the store. A commit is then done and reading resumes.
   * 
   */
  override def run(): Unit = {
    
    try {
      log.info("Loader for {} started", topic)
      
      // The number of kafka messages read since the last commit.
      var messageCount = 0  

      while (keepRunning) {
        iterator.next() match {
          case Success(block) =>
            messageCount += 1
            readMetric.mark()
            writeMessage(block)
            if (messageCount > messageCommitLimit) {
              commitWhenClear()
              messageCount = 0
            }
          case Failure(err: ConsumerTimeoutException) =>
            log.trace("consumer timeout reading {}", topic)
            commitWhenClear()
            messageCount = 0
          case Failure(err) =>
            // Some other Kafka reading error. Discard iterator and try again.
            discardIterator()
            messageCount = 0
        }
      }

      log.info(s"$topic loader is terminating")

      commitKafkaOffsets() // commit any outstanding offsets
      reader.close()

      log.info(s"$topic loader has terminated")
    } finally {
      shutdownPromise.success(())
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
  
  /** 
   * Commit the topic offsets
   * 
   * If the commit fails the iterator is closed and re-opened. Any messages already queued and/or
   * written to the store will be re-read and re-written. Since the writes are idempotent this 
   * causes no problem other than duplicate work.
   */
  private def commitKafkaOffsets(): Unit = {
    nonFatalCatch withTry {
      reader.commit()
    } recover {
      case err => 
        log.error(s"Unhandled exception committing kafka offsets for $topic", err)
        // State of kafka connection is unknown. Discard iterator, new one will be created
        discardIterator()
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
  
  /** 
   * When all of the in progress writes are done commit to Kafka
   */
  private def commitWhenClear(): Unit = {
    log.trace(s"committing ${Thread.currentThread.getId} ${writesInProcess.availablePermits}")
    writesInProcess.acquire(maxConcurrentWrites)
    commitKafkaOffsets()
    writesInProcess.release(maxConcurrentWrites)
  }

  /** Write the block to the Store. Update watchers when complete
    *  
    * @param block TaggedBlock from a Kafka message.
    */
  private def writeMessage(block: TaggedBlock): Unit = {
    val sliceFutures = block map writeSlice

    val allDone = Future.sequence(sliceFutures).map { updates => 
        val flattened = updates.flatten // remove Nones
        flattened
      }
    
    allDone.value.map { 
      case Success(updates) =>
        recordComplete(updates)
      case Failure(err)     =>
        log.error(s"Writes for $topic failed, retrying", err)
    }
  }

  /** 
   * Write a slice of column data to the store. 
   * 
   * @return a future that completes when the data has been written. 
   */
  private def writeSlice(slice: TaggedSlice[_,_])
      (implicit keyType: TypeTag[K]): Future[Option[ColumnUpdate[K]]] = 
  {
    val resultPromise = promise[Option[ColumnUpdate[K]]]()
    
    def withFixedType[U](): Unit = {
      implicit val valueType = slice.valueType
      log.trace(
        "loading {} events to column: {}  keyType: {}  valueType: {}",
        slice.events.length.toString, slice.columnPath, keyType, valueType
      )

      writesInProcess.acquire()  // running in loader thread here
      log.trace(s"got permit ${Thread.currentThread.getId} ${writesInProcess.availablePermits}")
      
      val timer = writeMetric.timerContext()
      val result =
        for {
          valueSerialize <- RecoverCanSerialize.tryCanSerialize[U](valueType).toFuture
          castEvents: Events[K, U] = castKind(slice.events)
          update <- writeEventsWithRetry(castEvents, slice.columnPath)(valueSerialize)
        } yield {
          update
        }
      
      result onComplete { tryResult => 
        timer.stop()
        log.trace(s"releasing permit ${Thread.currentThread.getId} ${writesInProcess.availablePermits} ${writesInProcess.hasQueuedThreads}")
        writesInProcess.release() 
        
        tryResult match {
          case Success(optUpdate) => resultPromise.success(optUpdate)
          case Failure(err)       => 
            log.error(s"writeBlocks failed $err")
            writeErrorsMetric.mark()
            resultPromise.failure(err)
       }
      }
    }
    
    withFixedType()
    
    resultPromise.future
  }

  /**
   * write some typed Events to storage retrying on error until it succeeds.
   * @return a future when that completes when the writing is done
   */
  private def writeEventsWithRetry[U: CanSerialize](
    events: Iterable[Event[K, U]], columnPath: String
  ): Future[Option[ColumnUpdate[K]]] =
  {
    events.lastOption match {
      case None =>
        log.warn(s"no last key for $columnPath. events empty: ${events.isEmpty}")
        Future.successful(None)
      case Some(event) =>
        batchSizeMetric.mark(events.size)
        val p = promise[Option[ColumnUpdate[K]]]()
        writeEventsWithRetry(events, columnPath, p)
        p.future
    }
  }

  /**
   * write some typed Events to storage. Notifies any watchers when complete
   * @return a future when that completes when the writing is done
   */
  private def writeEventsWithRetry[U: CanSerialize](
    events: Iterable[Event[K, U]], columnPath: String, p: Promise[Option[ColumnUpdate[K]]]
  ): Unit =
  {
    val futureColumn = writeableColumn[U](columnPath)
    futureColumn map { column =>
      def writeEvents(): Unit = {
        column.write(events) onComplete { 
          case Success(_) =>
            p.success(Some(ColumnUpdate[K](columnPath, events.last.argument)))
          case Failure(err) =>
            log.error(s"Retrying writing events for $columnPath. $err")
            // TODO: Add some retry delay here (maybe in akka rewrite?)
            writeEvents()
        }
      }
      writeEvents()
    }
  }

  /**
   * Get the writeable column for this column path.
   *
   * This should always work except if C* is down and the column is being created.
   * Keep trying until it succeeds.
   */
  private def writeableColumn[U: CanSerialize](
    columnPath: String
  ): Future[WriteableColumn[K, U]] =
  {
    val p = promise[WriteableColumn[K, U]]()

    def column(): Unit = {
      store.writeableColumn[K, U](columnPath) onComplete { case Success(writer) =>
        p.success(writer)
      case Failure(err) =>
        log.error(s"Retrying writeableColumn $columnPath. $err")
        // TODO: Add some retry delay here (maybe in akka rewrite?)
        column()
      }
    }

    column()

    p.future
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
