package nest.sparkle.loader.kafka

import scala.reflect.runtime.universe._
import scala.util.{Try, Success, Failure}
import scala.util.control.NonFatal

import nest.sparkle.loader.kafka.TypeTagUtil.typeTaggedToString
import nest.sparkle.loader.Loader.{Events, LoadingTransformer, TaggedBlock}
import nest.sparkle.store.cassandra.{CanSerialize, RecoverCanSerialize}
import nest.sparkle.util.KindCast.castKind
import nest.sparkle.util.Log

/**
 * Marks both decoding and transforming iterators
 */
trait TaggedBlockIterator 
  extends Iterator[Try[TaggedBlock]]
  with Log

/**
 * Wraps a Kafka ConsumerIterator to create an iterator that returns a TaggedBlock decoded from
 * the Kafka message.
 */
class DecodeIterator(source: KafkaIterator, decoder: KafkaKeyValues) 
  extends TaggedBlockIterator
{  
  /** ??? */
  private implicit val keySerialize = RecoverCanSerialize.tryCanSerialize[Long](typeTag[Long]).get
  
  /** Return Kafka iterator's hasNext. */
  override def hasNext: Boolean = source.hasNext
  
  /** Return next message from the Kafka iterator and convert it into TaggedBlock.
    * 
    */
  override def next() : Try[TaggedBlock] = {
    source.next() match {
      case Success(mm)  =>
        try {
          Success(convertMessage(mm.message()))
        } catch {
          case NonFatal(err) => Failure(err)
        }
      case Failure(err)      => Failure(err)
    }
  }

  /** Convert an Avro encoded record to a TaggedBlock
    * 
    * @param record Message read from Kafka. Expected to be decoded by decoder.
    *               
    * @return TaggedBlock created from message.
    */
  private def convertMessage(record: ArrayRecordColumns): TaggedBlock = {
    val columnPathIds = {
      val ids = decoder.metaData.ids zip record.ids map {
        case (NameTypeDefault(name, typed, default), valueOpt) =>
          val valueOrDefault = valueOpt orElse default orElse {
            throw DecodeIterator.NullableFieldWithNoDefault(name)
          }
          typeTaggedToString(valueOrDefault.get, typed)
      }
      ids.foldLeft("")(_ + "/" + _).stripPrefix("/")
    }

    val block =
      record.typedColumns(decoder.metaData).map { taggedColumn =>
        val columnPath = decoder.columnPath(columnPathIds, taggedColumn.name)

        /** do the following with type parameters matching each other
          * (even though our caller will ultimately ignore them) */
        def withFixedTypes[T, U]() = {
          val typedEvents = taggedColumn.events.asInstanceOf[Events[T, U]]
          val keyType: TypeTag[T] = castKind(taggedColumn.keyType)
          val valueType: TypeTag[U] = castKind(taggedColumn.valueType)
          TaggedSlice[T, U](columnPath, typedEvents)(keyType, valueType)
        }
        withFixedTypes[Any, Any]()
      }

    log.trace(s"convertMessage: got block.length ${block.length}  head:${block.headOption}")
    block
  }
  
}

object DecodeIterator
{
  def apply(source: KafkaIterator, decoder: KafkaKeyValues): DecodeIterator =
    new DecodeIterator(source, decoder)
  
  /** thrown if a nullable id field is null and no default value was defined */
  case class NullableFieldWithNoDefault(msg: String) // format: OFF
    extends RuntimeException(s"nullable id field is null and no default value was defined: $msg") // format: ON
}


/**
 * Take a TaggedBlockIterator and transform it using transformer.
 */
class TransformIterator(source: TaggedBlockIterator, transformer: LoadingTransformer) 
  extends TaggedBlockIterator
{  
  /** Return source's hasNext. */
  override def hasNext: Boolean = source.hasNext
  
  /** Return next message from the Kafka iterator and convert it into TaggedBlock.
    * 
    */
  override def next() : Try[TaggedBlock] = {
    source.next() match {
      case Success(block)  =>
        try {
          Success(transformer.transform(block))
        } catch {
          case NonFatal(err) => Failure(err)
        }
      case Failure(err)      => Failure(err)
    }
  }
}


object TransformIterator
{
  def apply(source: TaggedBlockIterator, transformer: LoadingTransformer): TransformIterator =
    new TransformIterator(source, transformer)
}
