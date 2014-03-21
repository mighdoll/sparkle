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
import nest.sparkle.loader.kafka.AvroSupport.schemaFromString

object MillisDoubleAvro {
  val avroJson ="""
    {
      "type": "record", 
      "name": "MillitimeDouble",
      "fields" : [
        {"name": "id", "type": "string"},
        {"name": "time", "type": "long"},
        {"name": "value", "type": "double"}
      ]
    }"""
    
  val schema = schemaFromString(avroJson)
}

class MillisDoubleSchemaFinder extends FixedAvroSchema(MillisDoubleAvro.schema,
  SchemaParseInfo(
    name = "Latency",
    idField = "id",
    keyField = "time",
    valueFields = Seq("value")
  )
)

object MillisDoubleArrayAvro {
  val elementJson = """
      { "name":"element", 
        "type":"record",
        "fields":[ 
          { "name":"time", "type":"long" }, 
          { "name":"value", "type":"double" } 
        ]
      }"""
    
  val arrayJson = s"""
      { "type": "array",
        "items": $elementJson
      }"""

  val avroJson = s"""
      { "type":"record",
        "name":"Latency",
        "fields":[
          { "name":"id", "type":"string" },
          { "name":"elements", "type": $arrayJson }
        ]
      }"""
    
  val schema = schemaFromString(avroJson)
  val elementSchema = schemaFromString(elementJson)
  val arraySchema = schemaFromString(arrayJson)
}

class MillisDoubleArrayFinder extends FindDecoder {
  def decoderFor(topic:String):KafkaDecoder = {
    val schema = MillisDoubleArrayAvro.schema
    val extractor = AvroExtractors.arrayRecords(schema)
    AvroExtractor(schema, extractor)
  }  
}


