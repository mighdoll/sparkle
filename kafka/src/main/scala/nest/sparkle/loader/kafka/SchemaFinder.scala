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

import org.apache.avro.Schema

trait SchemaFinder {
  def schemaFor(topic:String):SchemaWithParseInfo
}

case class SchemaWithParseInfo(schema:Schema, parseInfo:SchemaParseInfo)    
case class SchemaParseInfo(name:String, idField:String, keyField:String, valueFields:Seq[String])    // consider keyFields as a Seq

class FixedAvroSchema(schema:Schema, parseInfo:SchemaParseInfo) extends SchemaFinder {
  def schemaFor(topic:String):SchemaWithParseInfo = 
    SchemaWithParseInfo(schema, parseInfo)
}


