package nest.sparkle.time.protocol

import nest.sparkle.util.LogUtil
import ch.qos.logback.classic.{Level, Logger}
import nest.sparkle.store.cassandra.CassandraStoreTestConfig
import org.scalatest.{Matchers, FunSuite}
import nest.sparkle.util.FutureAwait.Implicits._
import nest.sparkle.util.MetricsInstrumentation
import scala.collection.JavaConverters._

class TestProtocolMetrics extends FunSuite with Matchers with
    CassandraStoreTestConfig with StreamRequestor {

  override def testConfigFile: Option[String] = Some("tests")

  test("protocol requests report a timing value on success") {
    withLoadedFile("epochs.csv") { (store, system) =>
      val request = stringRequest("epochs/p99", "reduceSum")
      val service = new TestServiceWithCassandra(store, system)
      val response = service.sendDataMessage(request).await
      val timers = MetricsInstrumentation.registry.getTimers.asScala
      val timer = timers("data-request-http")
      timer.getCount >= 1 shouldBe true
    }
  }

  test("protocol requests report a timing value on error") {
    withLoadedFile("epochs.csv") { (store, system) =>
      val request = stringRequest("epochs/not-really-there", "reduceSum")
      val service = new TestServiceWithCassandra(store, system)
      LogUtil.withLogLevel(ProtocolError.getClass, "ERROR") {
        val response = service.sendDataMessage(request).await
        val timers = MetricsInstrumentation.registry.getTimers.asScala
        val timer = timers("data-request-http-error")
        timer.getCount >= 1 shouldBe true
      }
    }
  }

}
