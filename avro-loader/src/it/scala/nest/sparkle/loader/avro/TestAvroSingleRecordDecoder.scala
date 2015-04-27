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

import nest.sparkle.loader.ArrayRecordColumns
import nest.sparkle.store.Event
import org.apache.avro.generic.GenericData
import org.scalatest.{Matchers, FunSuite}

class TestAvroSingleRecordDecoder extends FunSuite with Matchers {
  test("trip through the AvroSingleRecordDecoder") {
    val schema = MillisDoubleAvro.schema

    val record = new GenericData.Record(schema)
    record.put("id", "abc")
    record.put("time", 1L)
    record.put("value", 2.1)

    val decoder = AvroSingleRecordDecoder.decoder(schema = schema,
      idFields = Seq(("id", None)),
      keyField = "time")

    val resultColumns: ArrayRecordColumns = decoder.decodeRecord(record)

    resultColumns.ids.size shouldBe 1
    resultColumns.ids(0) match {
      case Some(id) => id.toString() shouldBe "abc"
      case _ => throw new Exception("id not in the ArrayRecordColumn")
    }

    resultColumns.columns.size shouldBe 1
    resultColumns.columns(0).size shouldBe 1

    val resultEvent = resultColumns.columns(0)(0).asInstanceOf[Event[Long,Double]]
    resultEvent.key shouldBe 1L
    resultEvent.value shouldBe 2.1
  }
}

