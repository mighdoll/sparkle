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
import nest.sparkle.loader.avro.AvroRecordGenerators._
import nest.sparkle.store.Event
import org.scalatest.{Matchers, FunSuite}

class TestAvroArrayDecoder extends FunSuite with Matchers {
  test("trip through the AvroArrayDecoder") {
    val schema = MillisDoubleArrayAvro.schema
    val latencyRecord = makeLatencyRecord("abc123", "xyz", Seq(1L -> 13.1))

    val decoder = AvroArrayDecoder.decoder(schema = schema,
                                           arrayField = "elements",
                                           idFields = Seq(("id1", None),("id2", None)),
                                           keyField = "time")

    val resultColumns: ArrayRecordColumns = decoder.decodeRecord(latencyRecord)

    resultColumns.ids.size shouldBe 2
    resultColumns.ids(0) match {
      case Some(id) => id.toString() shouldBe "abc123"
      case _ => throw new Exception("id1 not in the ArrayRecordColumn")
    }

    resultColumns.ids(1) match {
      case Some(id) => id.toString() shouldBe "xyz"
      case _ => throw new Exception("id2 not in the ArrayRecordColumn")
    }

    resultColumns.columns.size shouldBe 1
    resultColumns.columns(0).size shouldBe 1

    val resultEvent = resultColumns.columns(0)(0).asInstanceOf[Event[Long,Double]]
    resultEvent.argument shouldBe 1L
    resultEvent.value shouldBe 13.1
  }
}
