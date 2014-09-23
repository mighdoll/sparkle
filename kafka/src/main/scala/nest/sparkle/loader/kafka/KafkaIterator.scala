package nest.sparkle.loader.kafka

import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal
import scala.reflect.runtime.universe._
import scala.language.existentials

import com.github.nscala_time.time.Implicits._

import kafka.consumer.{ConsumerTimeoutException, ConsumerIterator}
import kafka.message.MessageAndMetadata
import kafka.serializer.Decoder

import nest.sparkle.util.{RetryManager, Log}

import KafkaIterator._

/**
 * Wraps a Kafka ConsumerIterator to create an iterator that returns a Try.
 * 
 * Kafka messages are converted to TaggedBlocks which may be optionally transformed.
 */
class KafkaIterator[T: Decoder](val reader: KafkaReader[T]) 
  extends Iterator[Try[MessageAndMetadata[String, T]]]
  with Log
{  
  /** The current consumer iterator */
  private lazy val consumerIterator = createConsumerIterator

  /** Return consumer iterator's hasNext. 
    * 
    * If the consumer iterator times out keep trying until we get a true or false.
    * 
    * Note that false can not be returned if a ConsumerTimeoutException is thrown as that
    * causes the iterator to terminate prematurely.
    */
  override def hasNext: Boolean = {
    var done = false
    var result = false
    while (!done) {
      try {
        result = consumerIterator.hasNext()
        done = true
      } catch {
        case err: ConsumerTimeoutException =>
      }
    }
    
    result
  }
  
  /** Return next item as a Success.
    * 
    * If an exception is throw it is returned as a Failure.
    * The caller will likely discard this iterator and close the reader for everything except
    * a ConsumerTimeoutException, which is expected.
    */
  override def next() : Try[MessageAndMetadata[String, T]] = {
    try {
      val mm = consumerIterator.next()
      Success(mm)
    } catch {
      case NonFatal(err) =>
         Failure(err)
    }
  }
  
  /**
   * Get a new kafka consumer iterator from the reader.
   * 
   * Keeps trying if it fails. Current thread is blocked.
   * 
   * Kafka unfortunately doesn't derive all of it's exceptions from a base class or trait.
   * This leaves us no easy way to catch just Kafka errors. We use execute that retries on all
   * non-fatal errors. It would be better to just retry on known Kafka connection exceptions.
   * 
   * @return iterator
   */
  private def createConsumerIterator = {
    val retryManager = RetryManager(initialConnectRetryWait, maxConnectRetryWait)
    retryManager.execute[ConsumerIterator[String,T]](reader.consumerIterator())
  }
  
}

object KafkaIterator
{
  /** Maximum time to wait between consumer iterator create attempts */
  val initialConnectRetryWait = 200.millis.toDuration
  
  /** Maximum time to wait between consumer iterator create attempts */
  val maxConnectRetryWait = 1.minute.toDuration
  
  def apply[T: Decoder](reader: KafkaReader[T]): KafkaIterator[T] =
    new KafkaIterator[T](reader)
}
