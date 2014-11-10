package nest.sparkle.loader.kafka

import scala.concurrent.duration._

import spray.http.StatusCodes._
import spray.http.MediaTypes.`application/json`
import spray.testkit.ScalatestRouteTest

import akka.actor.ActorRefFactory

import spray.json._

import nest.sparkle.util.kafka.{KafkaTestSuite, KafkaBroker, KafkaTopic, KafkaGroupOffsets}
import nest.sparkle.util.kafka.KafkaJsonProtocol._

class TestKafkaLoaderAdminService
  extends KafkaTestSuite
    with ScalatestRouteTest
    with KafkaTestConfig 
    with KafkaLoaderAdminService
{
  override def actorRefFactory: ActorRefFactory = system
  def executionContext = system.dispatcher
  
  // Some of the requests currently take a very long time
  implicit val routeTestTimeout = RouteTestTimeout(1.minute)
  
  test("The list of brokers is correct") {
    Get("/brokers") ~> allRoutes ~> check {
      assert(handled, "request was not handled")
      assert(status == OK, "response not OK")
      mediaType shouldBe `application/json`
      val json = body.asString
      
      json.length > 0 shouldBe true
      val ast = json.asJson
      val brokers = ast.convertTo[Seq[KafkaBroker]]
      brokers.length shouldBe 1
      val broker = brokers(0)
      broker.id shouldBe 0
      broker.host shouldBe "localhost"
      broker.port shouldBe 9092
    }
  }
  
  test("The list of topics includes the test topic") {
    Get("/topics") ~> allRoutes ~> check {
      assert(handled, "request was not handled")
      assert(status == OK, "response not OK")
      mediaType shouldBe `application/json`
      val json = body.asString
      
      json.length > 0 shouldBe true
      val ast = json.asJson
      val topics = ast.convertTo[Seq[KafkaTopic]]
      topics.exists(_.name.contentEquals(TOPIC)) shouldBe true
      val optTopic = topics.find(_.name.contentEquals(TOPIC))
      optTopic match {
        case Some(topic) =>
          topic.partitions.length shouldBe NUM_PARTITIONS
          topic.partitions.zipWithIndex foreach { case (partition, i) =>
            partition.id shouldBe i
            partition.leader shouldBe 0
            partition.brokerIds.length shouldBe 1
            partition.brokerIds(0) shouldBe 0
          }
        case _       => fail("topic not found in topics")
      }
    }
  }
  
  test("The list of consumer groups includes the test group") {
    Get("/groups") ~> allRoutes ~> check {
      assert(handled, "request was not handled")
      assert(status == OK, "response not OK")
      mediaType shouldBe `application/json`
      val json = body.asString
      
      json.length > 0 shouldBe true
      val ast = json.asJson
      val groups = ast.convertTo[Seq[String]]
      groups.contains(CONSUMER_GROUP) shouldBe true
    }
  }
  
  test("The list of consumer group topic offsets is correct") {
    Get("/offsets") ~> allRoutes ~> check {
      assert(handled, "request was not handled")
      assert(status == OK, "response not OK")
      mediaType shouldBe `application/json`
      val json = body.asString
      
      json.length > 0 shouldBe true
      val ast = json.asJson
      val groups = ast.convertTo[Seq[KafkaGroupOffsets]]
      assert(groups.length > 1, "no consumer groups found")
      val optGroup = groups.find(_.group.contentEquals(CONSUMER_GROUP))
      optGroup match {
        case Some(group)  =>
          assert(group.topics.size == 1, "not one topic in the consumer group")
          assert(group.topics.contains(TOPIC), "topic not in the consumer group")
          val topic = group.topics(TOPIC)
          assert(topic.partitions.length == NUM_PARTITIONS, s"${topic.topic} does not have $NUM_PARTITIONS partitions")
          topic.partitions.zipWithIndex foreach { case (offset,i) =>
            assert(offset.partition == i, s"${topic.topic}:$i partition id doesn't equal index")
            assert(offset.offset == 2, s"${topic.topic}:$i partition offset doesn't equal 2")
          }
        case _            => fail(s"consumer group $CONSUMER_GROUP not found")
      }
    }
  }

}
