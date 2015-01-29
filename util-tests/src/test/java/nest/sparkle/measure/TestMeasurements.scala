package nest.sparkle.measure

import java.lang.{Long => JLong}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory

import org.scalatest.{FunSuite, Matchers}

import nest.sparkle.test.SparkleTestConfig
import nest.sparkle.util.ConfigUtil.sparkleConfigName
import nest.sparkle.util.RandomUtil.randomAlphaNum
import nest.sparkle.util.{ConfigUtil, MetricsInstrumentation}


class TestMeasurements extends FunSuite with Matchers with SparkleTestConfig {

  def withMeasurements(fn: (Measurements, Path) => Unit) {
    val spanFile = s"/tmp/TestMeasurements-${randomAlphaNum(3)}.tsv"
    val overridenConfig = {
      val rootConfig = ConfigFactory.load("testMeasure.conf")
      val tsvConfig = (s"$sparkleConfigName.measure.tsv-gateway.file" -> spanFile)
      ConfigUtil.modifiedConfig(rootConfig, tsvConfig)
    }
    val spanFilePath = Paths.get(spanFile)

    import scala.concurrent.ExecutionContext.Implicits.global
    val measurement = new ConfiguredMeasurements(overridenConfig)
    try {
      fn(measurement, spanFilePath)
    } finally {
      measurement.close()
      Files.deleteIfExists(spanFilePath)
    }
  }

  def publishSpan()(implicit measurements: Measurements): String = {
    val name = "short-test"
    val traceId = TraceId("foo") // TODO replace with RandomUtil.RandomAlphaNum
    val span = Span.startNoParent(name, traceId)
    span.complete()
    name
  }

  test("publish a span to coda-metrics") {
    withMeasurements { (measurements, _) =>
      val name = publishSpan()(measurements)
      val timers = MetricsInstrumentation.registry.getTimers.asScala
      val x = timers(name)
      x.getCount shouldBe 1
    }
  }

  test("publish a span to a .tsv file") {
    withMeasurements { (measurements, tsvFile) =>
      val spanName = publishSpan()(measurements)
      measurements.flush()

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