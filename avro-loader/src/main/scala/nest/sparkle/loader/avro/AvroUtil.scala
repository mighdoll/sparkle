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

import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.GenericRecord
import spray.json._

/** handy utilities for printing out avro data */
object AvroUtil {
  
  /** return a pretty printed json string from a generic avro record */
  def prettyRecord(record: GenericRecord): String = {
    val recordString = record.toString
    val recordJson = recordString.parseJson
    recordJson.prettyPrint
  }

  /** return a pretty printed json string from avro binary data and an avro Schema */
  def prettyAvro(avroBytes: Array[Byte], schema: Schema): String = {
    val record = new AvroGenericSerializer(schema, schema).fromBytes(avroBytes)
    prettyRecord(record)
  }


  /** Return an avro schema parsed from an avro json schema string */
  def schemaFromString(json: String): Schema = {
    val parser = new Parser()
    parser.parse(json)
  }
}