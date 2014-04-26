package nest.sparkle.loader.kafka

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import org.scalatest.{ FunSuite, Matchers }

import rx.lang.scala.Observable

import nest.sparkle.util.ObservableFuture._
import nest.sparkle.loader.kafka.KafkaTestTopic.withKafkaTestTopic

class TestKafkaSlowResponse extends FunSuite with Matchers with KafkaTestConfig {
  import ExecutionContext.Implicits.global

  override def configOverrides = super.configOverrides :+
    ("sparkle-time-server.kafka-loader.kafka-reader.consumer.timeout.ms" -> "300")

  test("reconnect and continue reading after a consumer timeout") {
    withKafkaTestTopic(rootConfig){ kafka =>
      val toWrite = Observable.interval(500.milliseconds).map(_.toString).take(3)
      kafka.writer.writeStream(toWrite)
      
      val stream = kafka.reader.stream()
      val committing = stream.doOnEach{ _ => kafka.reader.commit() }
      val result = committing.take(3).toBlockingObservable.toList
      result shouldBe Seq("0", "1", "2")
    }
  }
}