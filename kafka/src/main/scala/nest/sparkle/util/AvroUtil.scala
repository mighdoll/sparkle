package nest.sparkle.util
import org.apache.avro.generic.GenericRecord
import spray.json._
import org.apache.avro.Schema
import nest.sparkle.loader.kafka.AvroSupport

/** handy utilities for printing out avro data */
object AvroUtil {
  
  /** return a pretty printed json string from a generic avro record */
  def prettyRecord(record: GenericRecord): String = {
    val recordString = record.toString
    val recordJson = recordString.asJson
    recordJson.prettyPrint
  }

  /** return a pretty printed json string from avro binary data and an avro Schema */
  def prettyAvro(avroBytes: Array[Byte], schema: Schema): String = {
    val genericDecoder = AvroSupport.genericDecoder(schema)
    val record = genericDecoder.fromBytes(avroBytes)
    prettyRecord(record)
  }

}