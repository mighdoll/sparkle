/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.loader.kafka
import org.apache.avro.Schema.Parser
import org.apache.avro.Schema
import kafka.serializer.Encoder
import kafka.serializer.Decoder
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.BinaryEncoder
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayOutputStream
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.generic.GenericDatumReader
import java.io.ByteArrayInputStream

/** encoders, decoders and utilities for working with avro encoded data in kafka */
object AvroSupport {
  
  /** Return an avro schema parsed from an avro json schema string */
  def schemaFromString(json: String): Schema = {
    val parser = new Parser()
    parser.parse(json)
  }

  /** Return a kafka encoder that encodes avro GenericRecords (the avro objects are of a fixed schema
   *  but the type is unknown at static compilation time) */
  def genericEncoder(schema: Schema): Encoder[GenericRecord] = {
    new Encoder[GenericRecord] {
      def toBytes(data: GenericRecord): Array[Byte] = {
        val bytesOut = new ByteArrayOutputStream
        val encoder = EncoderFactory.get().directBinaryEncoder(bytesOut, null)
        val writer = new GenericDatumWriter[GenericRecord](schema)
        writer.write(data, encoder)
        bytesOut.toByteArray
      }
    }
  }

  /** Return a kafka decoder that decodes avro GenericRecords (the avro objects are of a fixed schema
   *  but the type is unknown at static compilation time) */
  def genericDecoder(schema: Schema): Decoder[GenericRecord] = {
    new Decoder[GenericRecord] {
      def fromBytes(bytes:Array[Byte]): GenericRecord = {
        val reader = new GenericDatumReader[GenericRecord](schema)
        val byteStream = new ByteArrayInputStream(bytes)
        val decoder = DecoderFactory.get().directBinaryDecoder(byteStream, null)
        reader.read(null, decoder)
      }
    }
  }
  
  // TODO add genericDecoder where the writer and reader schemas are different
  
}
