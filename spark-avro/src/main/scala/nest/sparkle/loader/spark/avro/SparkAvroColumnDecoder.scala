package nest.sparkle.loader.spark.avro

import nest.sparkle.loader.ArrayRecordDecoder
import nest.sparkle.loader.{ArrayRecordColumns, ArrayRecordMeta, KeyValueColumn}
import org.apache.avro.generic.GenericRecord

/**
 * Create a KeyValueColumn decoder for avro data files read through Spark
 * @param event - name of the Avro event to read
 * @param decoder
 * @param prefix
 */
case class SparkAvroColumnDecoder (event: String,
                                   decoder: ArrayRecordDecoder[GenericRecord],
                                   override val prefix: String ) extends KeyValueColumn {

  override def metaData: ArrayRecordMeta = decoder.metaData
  override def suffix: String = event

  def fromAvro(record: GenericRecord) : ArrayRecordColumns = {
    decoder.decodeRecord(record)
  }
}

