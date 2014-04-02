package nest.sparkle.loader.kafka

import kafka.serializer.Decoder
import org.apache.avro.Schema

/** contains the event decoder and meta data decoders to convert a stream of
  * of binary kafka records into a stream of sparkle data events. */
sealed trait KafkaColumnDecoder[T] extends Decoder[T] {
  def columnName: String
  def dataSetPath: String
}

/** A KafkaColumnDecoder for records that contain a single id and multiple rows,
 *  where each row contains a single key and multiple values. 
 *  
 *  e.g. a record of the following structure: 
 *  id: "id",
 *  row: [[key,[value,..],
 *        [key,[value,..]
 *       ] 
 */
trait KafkaKeyValues extends KafkaColumnDecoder[IdKeyAndValues] {
  /** report the types of the id, the key, and the values */
  def types:IdKeyAndValuesTypes
}

/** a KafkaColumnDecoder for id,[value] only records */
// trait KafkaValues // LATER 

/** KafkaColumnDecoder for avro encoded key,value records */
case class AvroColumnDecoder(
    override val dataSetPath: String,  // format: OFF
    val schema: Schema, 
    val decoder: IdKeyAndValuesDecoder) extends KafkaKeyValues { // format: ON

  def fromBytes(bytes: Array[Byte]): IdKeyAndValues = {
    val genericDecoder = AvroSupport.genericDecoder(schema)
    val record = genericDecoder.fromBytes(bytes)
    decoder.decodeRecord(record)
  }
  
  def types:IdKeyAndValuesTypes = decoder.types

  override def columnName: String = schema.getName()
}
