package nest.sparkle.loader.spark.avro

import nest.sparkle.loader.Loader.TaggedBlock2
import nest.sparkle.util.Log

import org.apache.avro.generic.GenericRecord
import org.apache.avro.mapreduce.AvroKeyInputFormat
import org.apache.avro.mapred.AvroKey
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext

import org.apache.hadoop.io.NullWritable

import nest.sparkle.loader.spark.EventReader
import nest.sparkle.loader.{ArrayRecordColumns, KeyValueColumnConverter}

import scala.util.{Success, Failure}

/**
 * Reads Avro data files and maps them into TaggedBlocks using a SparkAvroColumnDecoder
 */
class SparkAvroEventReader(decoder: SparkAvroColumnDecoder)
    extends EventReader[AvroKey[GenericRecord], NullWritable] with Log {

  override def events(input: String,
                      sc: SparkContext) : RDD[TaggedBlock2] = {
    sc.newAPIHadoopFile[AvroKey[GenericRecord], NullWritable, AvroKeyInputFormat[GenericRecord]] (input)
    .map { tuple =>
      val (key, value) = tuple
      val record: ArrayRecordColumns = decoder.fromAvro(key.datum)

      val blockTry = KeyValueColumnConverter.convertMessage(decoder, record)

      blockTry match {
        case Success(block) => block
        case Failure(err) => {
          log.error(s"error reading from $input: ${err.printStackTrace()}")
          throw new Exception(s"error reading from $input")
        }
      }
      ???
    }
  }
}