package nest.sparkle.loader.kafka

import kafka.serializer.Decoder
import org.apache.avro.Schema

/** contains the event decoder and meta data decoders to convert a stream of
  * of binary kafka records into a stream of sparkle data events.
  *
  * The columnPath produced by users of the decoder is expected to be:
  * prefix/id/suffix/columnName
  */
sealed trait KafkaColumnDecoder[T] extends Decoder[T] {
  /** columnPath after the id (including the columnName itself) */
  lazy val columnPathSuffix: String = appendSlash(suffix) 

  /** columnPath before the id */
  lazy val columnPathPrefix: String = appendSlash(prefix)

  /** subclass should override to add a suffix to the columnPath */
  protected def suffix: String = ""

  /** columnPath prefix, without trailing slash */
  protected def prefix: String = ""

  /** append a slash if the string is non-empty */
  private def appendSlash(string: String): String = {
    if (string == "") {
      string
    } else {
      string + "/"
    }
  }

  /** return the full columnPath for this decoder given an id and columnName */
  def columnPath(id:String, columnName:String):String = 
      s"$columnPathPrefix$id/$columnPathSuffix$columnName"

}

/** A KafkaColumnDecoder for records that contain a single id and multiple rows,
  * where each row contains a single key and multiple values.
  *
  * e.g. a record of the following structure:
  * id: "id",
  * row: [[key,[value,..],
  *      [key,[value,..]
  *     ]
  */
trait KafkaKeyValues extends KafkaColumnDecoder[ArrayRecordColumns] {
  /** report the types of the id, the key, and the values */
  def metaData: ArrayRecordMeta
}

/** a KafkaColumnDecoder for id,[value] only records */
// trait KafkaValues // LATER 

/** KafkaColumnDecoder for avro encoded key,value records */
case class AvroColumnDecoder(// format: OFF
    val schema: Schema, 
    val decoder: ArrayRecordDecoder,
    override val prefix: String
   ) extends KafkaKeyValues { // format: ON

  override def fromBytes(bytes: Array[Byte]): ArrayRecordColumns = {
    val genericDecoder = AvroSupport.genericDecoder(schema)
    val record = genericDecoder.fromBytes(bytes)
    decoder.decodeRecord(record)
  }

  override def metaData: ArrayRecordMeta = decoder.metaData

  override def suffix: String = schema.getName()
}
