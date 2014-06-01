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
import org.apache.avro.generic.GenericRecord
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import scala.concurrent.ExecutionContext
import org.scalatest.prop.PropertyChecks
import org.scalatest.prop.Configuration.MinSuccessful
import nest.sparkle.loader.kafka.KafkaTestUtil.{ withTestAvroTopic, withTestReader }

class TestAvroKafka extends FunSuite with Matchers with PropertyChecks with KafkaTestConfig {
  import ExecutionContext.Implicits.global
  import AvroRecordGenerators.Implicits.arbitraryMillisDoubleRecord
  test("read/write a few avro encoded elements from the kafka queue") {
    forAll(MinSuccessful(5)){ records: List[GenericData.Record] =>
      whenever(records.length > 0) {
        withTestAvroTopic(rootConfig, MillisDoubleAvro.schema) { testTopic =>
          testTopic.writer.write(records)
          withTestReader(testTopic){ reader =>
            val stream = reader.stream()
            val results = stream.take(records.length).toBlocking.toList
            results.length shouldBe records.length
            records zip results foreach {
              case (record, result) =>
                record.get("time") shouldBe result.get("time")
                record.get("value") shouldBe result.get("value")
            }
          }
        }
      }
    }

  }

}
