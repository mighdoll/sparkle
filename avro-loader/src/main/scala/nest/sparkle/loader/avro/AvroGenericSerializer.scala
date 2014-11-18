/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */
package nest.sparkle.loader.avro

import java.io.{ByteArrayOutputStream, ByteArrayInputStream}

import nest.sparkle.loader.SparkleSerializer
import org.apache.avro.generic.{GenericDatumWriter, GenericDatumReader, GenericRecord}
import org.apache.avro.io.{EncoderFactory, DecoderFactory}
import org.apache.avro.Schema

class AvroGenericSerializer(writerSchema: Schema, readerSchema: Schema) extends SparkleSerializer[GenericRecord] {
  def toBytes(data: GenericRecord): Array[Byte] = {
    val bytesOut = new ByteArrayOutputStream
    val encoder = EncoderFactory.get().directBinaryEncoder(bytesOut, null)
    val writer = new GenericDatumWriter[GenericRecord](writerSchema)
    writer.write(data, encoder)
    bytesOut.toByteArray
  }

  def fromBytes(bytes:Array[Byte]): GenericRecord = {
    val reader = new GenericDatumReader[GenericRecord](readerSchema)
    val byteStream = new ByteArrayInputStream(bytes)
    val decoder = DecoderFactory.get().directBinaryDecoder(byteStream, null)
    reader.read(null, decoder)
  }
}