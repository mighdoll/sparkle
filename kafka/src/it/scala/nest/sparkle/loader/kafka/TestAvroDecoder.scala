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

import org.scalatest.FunSuite
import org.scalatest.Matchers
import nest.sparkle.loader.kafka.AvroSupport._
import org.apache.avro.generic.GenericData
import java.util.ArrayList

class TestAvroDecoder extends FunSuite with Matchers {
  test("round trip MillisDoubleAvro through the avro binary decoder") {
    val schema = MillisDoubleAvro.schema
    val encoder = genericEncoder(schema)
    val decoder = genericDecoder(schema)
    val record = new GenericData.Record(schema)
    record.put("id", "abc")
    record.put("time", 1L)
    record.put("value", 2.1)
    val bytes = encoder.toBytes(record)
    val result = decoder.fromBytes(bytes)
    result.get("id").toString shouldBe "abc"
    result.get("time") shouldBe 1L
    result.get("value") shouldBe 2.1
  }

  test("round trip MillisDoubleArrayAvro through the avro binary decoder") {
    val schema = MillisDoubleArrayAvro.schema
    val encoder = genericEncoder(schema)
    val decoder = genericDecoder(schema)

    val elementArray = {
      val collection = new ArrayList[GenericData.Record]()
      val array = new GenericData.Array(MillisDoubleArrayAvro.arraySchema, collection)
      val element = new GenericData.Record(MillisDoubleArrayAvro.elementSchema)
      element.put("time", 1L)
      element.put("value", 13.1)
      array.add(element)
      array
    }

    val latencyRecord = {
      val record = new GenericData.Record(schema)
      record.put("id", "abc123")
      record.put("elements", elementArray)
      record
    }

    val bytes = encoder.toBytes(latencyRecord)
    val result = decoder.fromBytes(bytes)
    result.get("id").toString shouldBe "abc123"
    val resultElements = result.get("elements").asInstanceOf[GenericData.Array[GenericData.Record]]
    val resultElement = resultElements.get(0)
    resultElement.get("value") shouldBe 13.1
    resultElement.get("time") shouldBe 1L
  }


}
