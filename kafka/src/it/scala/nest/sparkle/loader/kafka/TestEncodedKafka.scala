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

import kafka.message.MessageAndMetadata
import org.scalatest.{ FunSuite, Matchers }
import org.scalatest.prop.PropertyChecks

import nest.sparkle.loader.kafka.KafkaTestUtil.{ withTestEncodedTopic, withTestReader }

class TestEncodedKafka extends FunSuite with Matchers with PropertyChecks with KafkaTestConfig {
  val stringSerde = KafkaTestUtil.stringSerde

  test("read/write a few encoded elements from the kafka queue"){
    forAll(MinSuccessful(5), MinSize(1)) { records: List[String] =>
      withTestEncodedTopic[String, Unit](rootConfig, stringSerde) { testTopic =>
        testTopic.writer.write(records)
        withTestReader(testTopic, stringSerde) { reader =>
          val iter = reader.messageAndMetaDataIterator()
          val results: List[MessageAndMetadata[String, String]] = iter.take(records.length).toList
          
          records zip results foreach {
            case (record, result) =>
              record shouldBe result.message()
              val msg = result.message()
              record shouldBe msg
          }
        }
      }
    }

  }

}
