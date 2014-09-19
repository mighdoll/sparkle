package nest.sparkle.loader.kafka

import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

import kafka.consumer.{ConsumerIterator, ConsumerTimeoutException}
import kafka.message.MessageAndMetadata

import nest.sparkle.util.Log

import KafkaIterator._

/**
 * Wraps a Kafka ConsumerIterator to create an iterator that returns a Try.
 * 
 * Kafka messages are converted to TaggedBlocks which may be optionally transformed.
 */
class KafkaIterator(val reader: KafkaReader[ArrayRecordColumns]) 
  extends Iterator[Try[MessageAndMetadata[String, ArrayRecordColumns]]]
  with Log
{  
  /** The current consumer iterator */
  private lazy val consumerIterator = createConsumerIterator

  /** Return consumer iterator's hasNext. */
  override def hasNext: Boolean = consumerIterator.hasNext()
  
  /** Return next item as a Success.
    * 
    * If an exception is throw it is returned as a Failure.
    * The caller will likely discard this iterator and close the reader for everything except
    * a ConsumerTimeoutException, which is expected.
    */
  override def next() : Try[MessageAndMetadata[String, ArrayRecordColumns]] = {
    try {
      val mm = consumerIterator.next()
      Success(mm)
    } catch {
      case e: ConsumerTimeoutException =>
        Failure(e)
      case NonFatal(err)               =>
        log.error("Exception calling hasNext()", err)
        Failure(err)
    }
  }
  
  /**
   * Get a new kafka consumer iterator from the reader.
   * 
   * Keeps trying if it fails. Current thread is blocked.
   * 
   * @return iterator
   */
  private def createConsumerIterator = {
    var iter: ConsumerIterator[String, ArrayRecordColumns] = null
    var done = false
    var sleepTime = 200L
    while (!done) {
      try {
        iter = reader.consumerIterator()
        done = true
      } catch {
        case NonFatal(err) =>
          log.error("Exception getting iterator: {}", err.getMessage)
          
          // Connection state is unknown, close it. A reconnect will be tried on next iterator attempt.
          reader.close()
          
          // Sleep with limited back-off
          Thread.sleep(sleepTime)
          sleepTime = {
            sleepTime match {
              case t if t >= maxConnectRetryWait => maxConnectRetryWait
              case _                             => sleepTime * 2L
            }
          }
      }
    }
    iter
  }
  
}

object KafkaIterator
{
  /** Maximum time to wait between consumer iterator create attempts */
  val maxConnectRetryWait = 60000L
  
  def apply(reader: KafkaReader[ArrayRecordColumns]): KafkaIterator =
    new KafkaIterator(reader)
}
