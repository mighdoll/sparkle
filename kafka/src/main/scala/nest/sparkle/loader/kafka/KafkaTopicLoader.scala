package nest.sparkle.loader.kafka

import java.util.concurrent.Semaphore

import com.google.common.util.concurrent.RateLimiter

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.concurrent.{Future, ExecutionContext, promise, Promise}
import scala.reflect.runtime.universe._
import scala.language.existentials
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal
import scala.util.control.Exception.nonFatalCatch

import com.typesafe.config.{Config, ConfigFactory}

import akka.actor.ActorSystem

import kafka.consumer.ConsumerTimeoutException

import nest.sparkle.loader._
import nest.sparkle.loader.Loader._
import nest.sparkle.datastream.DataArray
import nest.sparkle.store.WriteableStore
import nest.sparkle.store.cassandra.{WriteableColumn, CanSerialize, RecoverCanSerialize}
import nest.sparkle.util.{Instrumented, Log, Instance, ConfigUtil, Watched}
import nest.sparkle.util.TryToFuture.FutureTry

/**
 * A runnable that reads a topic from a KafkaReader that returns messages containing Avro encoded
 * records that contains an array of values and writes the values to the Sparkle store.
 */
class KafkaTopicLoader[K: TypeTag]( val rootConfig: Config,
                                    val store: WriteableStore,
                                    val topic: String,
                                    val decoder: KafkaKeyValues
) (implicit system: ActorSystem)
  extends Watched[ColumnUpdate[K]]
  with Runnable
  with Instrumented
  with Log
{  
  private val loaderConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("kafka-loader")
  
  implicit val execution: ExecutionContext = system.dispatcher 

  /** Evidence for key serializing when writing to the store */
  private implicit val keySerialize = RecoverCanSerialize.tryCanSerialize[K](typeTag[K]).get
  
  val reader = KafkaReader(topic, rootConfig, None)(decoder)

  val transformers = makeTransformers()
  
  /** Current Kafka iterator */
  private var currentIterator: Option[Iterator[Try[TaggedBlock]]] = None
  
  /** Maximum time between C* write retries */
  private val MaxDelay = 1 minute
  
  /** Initial delay between C* write retries */
  private val InitialDelay = 1 second

  /** For limiting the number of kafka messages read per second */
  val messageReadRateLimiter = readRateLimiter()

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

  /** Rate of deserialization errors */
  private val deserializationErrorsMetric = metrics.meter("deserialization-errors", metricPrefix)

  /** Rate of decoding errors */
  private val decodingErrorsMetric = metrics.meter("decoding-errors", metricPrefix)

  /** Rate of transformer errors */
  private val transformErrorsMetric = metrics.meter("transform-errors", metricPrefix)

  /** Rate of intentionally skipped messages */
  private val skippedMessagesMetric = metrics.meter("skipped-messages", metricPrefix)

  private val batchSizeMetric = metrics.histogram("event-batch-size", metricPrefix)
  
  /** Timer for writing to C* for this topic */
  private val writeMetric = metrics.timer("store-writes", metricPrefix)
  
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
      log.info(s"Loader for $topic started")
      
      // The number of kafka messages read since the last commit.
      var messageCount = 0

      // updates the message count, calls the specified function to process the
      // message, and then checks if we need to commit
      def processMessage()(fn: => Unit): Unit = {
        messageCount += 1
        readMetric.mark()
        fn
        if (messageCount > messageCommitLimit) {
          commitWhenClear()
          messageCount = 0
        }
      }

      while (keepRunning) {
        messageReadRateLimiter.acquire()
        iterator.next() match {
          case Success(block) =>
            processMessage() { writeMessage(block) }
          case Failure(err: SparkleDeserializationException) =>
            processMessage() {
              log.warn(s"failed to deserialize message from $topic", err)
              deserializationErrorsMetric.mark()
            }
          case Failure(err: ColumnDecoderException) =>
            processMessage() {
              log.warn(s"failed to decode message from $topic", err)
              decodingErrorsMetric.mark()
            }
          case Failure(err: TransformException) =>
            processMessage() {
              log.warn(s"failed to transform message from $topic", err)
              transformErrorsMetric.mark()
            }
          case Failure(err: ColumnPathNotDeterminable) =>
            processMessage() {
              log.trace(s"skipping message from $topic (${err.getMessage})")
              skippedMessagesMetric.mark()
            }
          case Failure(err: ConsumerTimeoutException) =>
            log.trace(s"consumer timeout reading $topic")
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

  /**
   * Creates a RateLimiter to limit the number of kafka messages read per second.
   * If a topic specific read rate is configured, that's used, otherwise the default
   * configured read rate is used.
   */
  private def readRateLimiter(): RateLimiter = {
    val topicList = loaderConfig.getConfigList("message-read-rate.topic-read-rates").asScala.toSeq
    val topicConfig = topicList.find { configEntry =>
      val regex = configEntry.getString("match").r
      regex.pattern.matcher(topic).matches()
    }

    val rate = topicConfig.map { configEntry =>
      configEntry.getDouble("topic-read-rate")
    }.getOrElse(loaderConfig.getDouble("message-read-rate.default-read-rate"))

    log.info(s"limiting read rate for topic $topic to: $rate messages per second")
    RateLimiter.create(rate)
  }

  /** Create the transformers, if any, for this topic.
    * The first pattern to match is used.
    */
  private def makeTransformers(): Option[Seq[LoadingTransformer]] = {
    val transformerList = loaderConfig.getConfigList("transformers").asScala.toSeq
    val transformerConfig = transformerList.find { configEntry =>
      val regex = configEntry.getString("match").r
      regex.pattern.matcher(topic).matches()
    }

    transformerConfig.map { configEntry =>
      val topicTransformerList = configEntry.getConfigList("topic-transformers").asScala.toSeq
      for {
        topicTransformerConfig <- topicTransformerList
      } yield {
        val className = topicTransformerConfig.getString("transformer")
        val transformerConfig = {
          if (topicTransformerConfig.hasPath("transformer-config"))
            topicTransformerConfig.getConfig("transformer-config")
          else ConfigFactory.empty
        }
        val transformer: LoadingTransformer = Instance.byName(className)(rootConfig, transformerConfig)
        transformer
      }
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
      val iter = transformers map { _ =>
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

  /** Indicates there's an issue transforming a block */
  case class TransformException(cause: Throwable) extends RuntimeException(cause)

  /** Transform the block. Only called if transformers is not None */
  private def transform(block: TaggedBlock): Try[TaggedBlock] = {
    val transformedBlock =
      transformers.get.foldLeft(Success(block) : Try[TaggedBlock]) { (transformedBlock, transformer) =>
        transformedBlock.flatMap(transformer.transform(_))
      }
    transformedBlock.transform(s => Success(s), e => Failure(TransformException(e)))
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
      log.trace(s"loading ${slice.dataArray.length} events to column: ${slice.columnPath}  keyType: $keyType  valueType: $valueType")

      writesInProcess.acquire()  // running in loader thread here
      log.trace(s"got permit ${Thread.currentThread.getId} ${writesInProcess.availablePermits}")
      
      val timer = writeMetric.timerContext()
      val result =
        for {
          valueSerialize <- RecoverCanSerialize.tryCanSerialize[U](valueType).toFuture
          castEvents = slice.dataArray.asInstanceOf[DataArray[K, U]]
          update <- writeEvents(castEvents, slice.columnPath)(valueSerialize)
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
            // this should never happen because we write until successful
            log.error(s"got unexpected failure writing events: $err")
            resultPromise.failure(err)
       }
      }
    }
    
    withFixedType()
    
    resultPromise.future
  }

  /**
   * write some typed Events to storage retrying on error until it succeeds.
   * @return a future with the updates to use for write notifications
   */
  private def writeEvents[U: CanSerialize](
    events: DataArray[K, U], columnPath: String
  ): Future[Option[ColumnUpdate[K]]] =
  {
    events.lastOption match {
      case None =>
        log.warn(s"no last key for $columnPath. events empty: ${events.isEmpty}")
        Future.successful(None)
      case Some(event) =>
        batchSizeMetric += events.size
        val p = promise[Option[ColumnUpdate[K]]]()
        writeEventsUntilSuccess(events, columnPath, p)
        p.future
    }
  }

  /**
   * write some typed Events to storage. Completes the passed Promise on success.
   */
  private def writeEventsUntilSuccess[U: CanSerialize](
    events: DataArray[K, U], columnPath: String, p: Promise[Option[ColumnUpdate[K]]]
  ): Unit =
  {
    val futureColumn = writeableColumn[U](columnPath)
    futureColumn map { column =>
      def doWrite(retryDelay: FiniteDuration): Unit = {
        column.writeData(events) onComplete {
          case Success(_) =>
            p.success(Some(ColumnUpdate[K](columnPath, events.last._1)))
          case Failure(err) =>
            log.error(s"Retrying writing events for $columnPath. $err")
            writeErrorsMetric.mark()
            system.scheduler.scheduleOnce(retryDelay) { 
              val nextDelay = retryDelay * 2 match {
                case n if n >= MaxDelay => MaxDelay
                case n                  => n
              }
              doWrite(nextDelay) 
            }
        }
      }
      doWrite(InitialDelay)
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

    def column(retryDelay: FiniteDuration): Unit = {
      store.writeableColumn[K, U](columnPath) onComplete { 
        case Success(writer) =>
          p.success(writer)
        case Failure(err) =>
          log.error(s"Retrying writeableColumn $columnPath. $err")
          system.scheduler.scheduleOnce(retryDelay) { 
            val nextDelay = retryDelay * 2 match {
              case n if n >= MaxDelay => MaxDelay
              case n                  => n
            }
            column(nextDelay) 
          }
      }
    }

    column(InitialDelay)

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
