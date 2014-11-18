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
package nest.sparkle.loader.avro

import nest.sparkle.loader.avro.AvroRecordGenerators.makeLatencyRecord

import org.apache.avro.generic.GenericData
import org.scalatest.{FunSuite, Matchers}

class TestAvroSerDe extends FunSuite with Matchers {
  test("round trip MillisDoubleAvro through the avro binary decoder") {
    val schema = MillisDoubleAvro.schema
    val serde = new AvroGenericSerializer(schema, schema)
    val record = new GenericData.Record(schema)
    record.put("id", "abc")
    record.put("time", 1L)
    record.put("value", 2.1)
    val bytes = serde.toBytes(record)
    val result = serde.fromBytes(bytes)
    result.get("id").toString shouldBe "abc"
    result.get("time") shouldBe 1L
    result.get("value") shouldBe 2.1
  }

  test("round trip MillisDoubleArrayAvro through the avro binary decoder") {
    val schema = MillisDoubleArrayAvro.schema
    val serde = new AvroGenericSerializer(schema, schema)

    val latencyRecord = makeLatencyRecord("abc123", "xyz", Seq(1L -> 13.1))

    val bytes = serde.toBytes(latencyRecord)
    val result = serde.fromBytes(bytes)
    result.get("id1").toString shouldBe "abc123"
    result.get("id2").toString shouldBe "xyz"
    val resultElements = result.get("elements").asInstanceOf[GenericData.Array[GenericData.Record]]
    resultElements.size shouldBe 1
    val resultElement = resultElements.get(0)
    resultElement.get("value") shouldBe 13.1
    resultElement.get("time") shouldBe 1L
  }

  test("round trip MillisDoubleArrayAvro with null id field through the avro binary decoder") {
    val schema = MillisDoubleArrayAvro.schema
    val serde = new AvroGenericSerializer(schema, schema)

    val latencyRecord = makeLatencyRecord("abc123", null, Seq(1L -> 13.1))

    val bytes = serde.toBytes(latencyRecord)
    val result = serde.fromBytes(bytes)
    result.get("id1").toString shouldBe "abc123"
    result.get("id2") shouldBe null
    val resultElements = result.get("elements").asInstanceOf[GenericData.Array[GenericData.Record]]
    val resultElement = resultElements.get(0)
    resultElement.get("value") shouldBe 13.1
    resultElement.get("time") shouldBe 1L
  }

}
