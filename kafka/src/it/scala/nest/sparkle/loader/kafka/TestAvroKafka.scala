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

import org.apache.avro.generic.GenericData

import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks

import nest.sparkle.loader.kafka.KafkaTestUtil.{ withTestAvroTopic, withTestReader }

class TestAvroKafka extends FunSuite with Matchers with PropertyChecks with KafkaTestConfig {
  import AvroRecordGenerators.Implicits.arbitraryMillisDoubleRecord

  test("read/write a few avro encoded elements from the kafka queue") {
    forAll(MinSuccessful(5), MinSize(1)) { records: List[GenericData.Record] =>
      withTestAvroTopic(rootConfig, MillisDoubleAvro.schema) { testTopic =>
        testTopic.writer.write(records)
        withTestReader(testTopic) { reader =>
          val stream = reader.messageAndMetaDataIterator
          val results = stream.take(records.length).toList
          results.length shouldBe records.length
          records zip results foreach {
            case (record, result) =>
              val msg = result.message()
              record.get("time") shouldBe msg.get("time")
              record.get("value") shouldBe msg.get("value")
          }
        }
      }
    }

  }

}
