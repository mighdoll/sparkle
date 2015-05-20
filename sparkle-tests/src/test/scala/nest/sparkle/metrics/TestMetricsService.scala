package nest.sparkle.metrics

import scala.compat.Platform.currentTime
import com.typesafe.config.Config
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers, Suite}
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest
import nest.sparkle.util.{ConfigUtil, LogUtil}
import nest.sparkle.test.SparkleTestConfig

/**
 * Test the Metrics HTTP server.
 */
class TestMetricsService 
  extends FunSuite 
  with Matchers
  with PropertyChecks
  with BeforeAndAfterAll
  with ScalatestRouteTest
  with MetricsService
  with SparkleTestConfig
{
  self: Suite =>
  
  def actorRefFactory = system
  
  // metric should be after this time
  val startTimestamp = currentTime / 1000L
  
  override def beforeAll() {    
    // Add a metric
    val counter = metrics.counter("counter")
    counter.inc()
    
    super.beforeAll()
  }

  test("metrics/graphite request returns metric line") {
    val path = "/metrics/graphite"
    Get(path) ~> routes ~> check {
      val metrics = responseAs[String].split("\n").collect {
        case Metric(metric) if metric.name == "counter.count" => metric
      }
      metrics.length shouldBe 1
      metrics(0).value.toInt shouldBe 1
      (metrics(0).timestamp >= startTimestamp) shouldBe true
    }
  }

  test("health returns ok") {
    val path = "/health"
    Get(path) ~> routes ~> check {
      response.status shouldBe StatusCodes.OK
      val text = responseAs[String]
      text shouldBe "ok"
    }
  }

}

case class Metric(name: String, value: String, timestamp: Long)

object Metric {
  val regex = "(.+) (.+) (\\d+)".r

  def unapply(str: String): Option[Metric] = str match {
    case regex(counter, value, timestamp) => Some(Metric(counter, value, timestamp.toLong))
    case _                                => None
  }
}
