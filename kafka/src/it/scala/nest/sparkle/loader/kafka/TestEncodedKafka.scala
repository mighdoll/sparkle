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
