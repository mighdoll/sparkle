package nest.sparkle.measure

import com.typesafe.config.ConfigFactory
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, Paths }
import nest.sparkle.util.MetricsInstrumentation
import org.scalatest.{ FunSuite, Matchers }
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import java.lang.{ Long => JLong }

class TestMeasurements extends FunSuite with Matchers {

  def withMeasurements(fn: Measurements => Unit) {
    val rootConfig = ConfigFactory.load("testMeasure.conf")
    val measurement = new ConfiguredMeasurements(rootConfig)
    fn(measurement)
  }

  def publishSpan()(implicit measurements: Measurements): String = {
    val name = "short-test"
    val traceId = TraceId("foo") // TODO replace with RandomUtil.RandomAlphaNum
    val span = Span.startNoParent(name, traceId, opsReport=true)
    span.complete()
    name
  }

  test("publish a span to coda-metrics") {
    withMeasurements { implicit measurements =>
      val name = publishSpan()
      val timers = MetricsInstrumentation.registry.getTimers.asScala
      val x = timers(name)
      x.getCount shouldBe 1
    }
  }

  test("publish a span to a .tsv file") {
    withMeasurements { implicit measurements =>
      val spanName = publishSpan()

      val tsvFile = Paths.get("/tmp/sparkle-measurements.tsv")
      val lines = Files.readAllLines(tsvFile, UTF_8).asScala
      lines.length shouldBe 2
      lines.head shouldBe "name\ttraceId\ttime\tduration"
      val Array(name, traceIdString, timeString, durationString) = lines(1).split("\t")
      name shouldBe spanName
      val time = JLong.parseLong(timeString)
      val duration = JLong.parseLong(durationString)
      val timeToNow = Math.abs(time/1000L - System.currentTimeMillis())
      timeToNow should be < 5.seconds.toMillis
      duration / (1000 * 1000) should be < 5.seconds.toMillis 
    }

  }

}