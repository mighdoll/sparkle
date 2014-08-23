package nest.sparkle.metrics

import scala.compat.Platform.currentTime

import com.typesafe.config.Config

import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers, Suite}
import spray.http.StatusCodes
import spray.testkit.ScalatestRouteTest

import nest.sparkle.util.{ConfigUtil, LogUtil}

// TODO: Use this when it is moved out of sparkle project.
//import nest.sparkle.test.SparkleTestConfig

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
{
  self: Suite =>
  
  def actorRefFactory = system
  
  var rootConfig: Config = null  // Cover your eyes
  
  // metric should be after this time
  val startTimestamp = currentTime / 1000L
  
  override def beforeAll() {
    rootConfig = ConfigUtil.configFromFile(None)
    LogUtil.configureLogging(rootConfig)
    
    // Add a metric
    val counter = metrics.counter("counter")
    counter.inc()
    
    super.beforeAll()
  }

  test("metrics/graphite request returns one metric line") {
    val path = "/metrics/graphite"
    Get(path) ~> routes ~> check {
      val lines = responseAs[String].split("\n")
      lines.length shouldBe 1
      val words = lines(0).split(" ")
      words.length shouldBe 3
      words(0) shouldBe "counter.count"
      words(1).toInt shouldBe 1
      (words(2).toInt >= startTimestamp) should be (true)
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
