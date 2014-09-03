package nest.sparkle.loader.kafka

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import rx.lang.scala.Observable

import spray.util._

import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.loader.kafka.KafkaEncoders.Implicits._
import nest.sparkle.loader.kafka.KafkaTestTopic.withKafkaTestTopic
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.ObservableFuture._

class TestKafkaSlowResponse extends FunSuite with Matchers with KafkaTestConfig {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def configOverrides = super.configOverrides :+
    (s"$sparkleConfigName.kafka-loader.kafka-reader.consumer.timeout.ms" -> "300")

  test("reconnect and continue reading after a consumer timeout") {
    withKafkaTestTopic(rootConfig){ kafka =>
      val toWrite = Observable.interval(500.milliseconds).map(_.toString).take(3)
      kafka.writer.writeStream(toWrite)
      
      // write to two partitions on this topic (2 partitions is the default config)
      val writer2 = KafkaWriter[String](kafka.topic, rootConfig)
      writer2.writeStream(toWrite)
      
      val stream = kafka.reader.stream()
      val committing = stream.doOnEach{ _ => kafka.reader.commit() }
      val result = committing.take(6).toFutureSeq.await
      
      // result should be 0,1,2 written twice
      val groups = result.groupBy { value => value}
      val found = groups.keys.toSet
      found shouldBe Set("0", "1", "2")
      groups.values.foreach { _.length shouldBe 2}
    }
  }
}