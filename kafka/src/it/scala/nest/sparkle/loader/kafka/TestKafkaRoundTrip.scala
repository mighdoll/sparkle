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

import rx.lang.scala.Observable

import spray.util._

import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.loader.kafka.KafkaTestTopic.withKafkaTestTopic
import nest.sparkle.util.RandomUtil.randomAlphaNum

class TestKafkaRoundTrip extends FunSuite with Matchers with KafkaTestConfig {
  
  def randomStrings(count: Int, length: Int = 3): Seq[String] = {
    (0 until count).map { _ => randomAlphaNum(length) }.toSeq
  }

  protected def roundTripTest(writerFn: (KafkaTestTopic, Seq[String]) => Unit) {
    val entries = 3
    withKafkaTestTopic(rootConfig) { kafka =>
      val testData = randomStrings(entries)
      writerFn(kafka, testData)
      val iter = kafka.reader.messageAndMetaDataIterator()
      val results = iter.map(_.message())
        .take(entries)
        .toList
      results shouldBe testData
    }
  }

  test("round trip 3 strings") {
    roundTripTest { (kafka, testData) =>
      kafka.writer.write(testData)
    }
  }

  test("round trip 3 strings, observable stream writer") {
    roundTripTest { (kafka, testData) =>
      kafka.writer.writeStream(Observable.from(testData))
    }
  }

  test("commitOffset() works, second reader picks up where first left off") {
    val entries = 4
    val testData = randomStrings(entries)
    val testId = randomAlphaNum(3)

    val kafka1 = new KafkaTestTopic(rootConfig, testId)
    kafka1.writer.write(testData)
    val stream1 = kafka1.reader.messageAndMetaDataIterator()
    val results1 = stream1.take(entries / 2).map(_.message()).toList

    kafka1.reader.commit()
    kafka1.reader.close() // trigger rebalancing immediately for test

    val kafka2 = new KafkaTestTopic(rootConfig, testId)
    val stream2 = kafka2.reader.messageAndMetaDataIterator()
    val results2 = stream2.take(entries / 2).map(_.message()).toList
    (results1 ++ results2) shouldBe testData

    kafka1.close()
    kafka2.close()
  }

}
