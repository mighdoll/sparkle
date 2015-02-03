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
import nest.sparkle.util.{RandomUtil, FileUtil, ConfigUtil, MetricsInstrumentation}


class TestMeasurements extends FunSuite with Matchers with SparkleTestConfig {

  def withMeasurements(fn: (Measurements, Path) => Unit) {
    val spanDirectory = s"/tmp/TestMeasurements-${randomAlphaNum(3)}"
    val overridenConfig = {
      val rootConfig = ConfigFactory.load("testMeasure.conf")
      val tsvConfig = (s"$sparkleConfigName.measure.tsv-gateway.directory" -> spanDirectory)
      ConfigUtil.modifiedConfig(rootConfig, tsvConfig)
    }
    val spanDirectoryPath = Paths.get(spanDirectory)

    import scala.concurrent.ExecutionContext.Implicits.global
    val measurement = new ConfiguredMeasurements(overridenConfig)
    try {
      fn(measurement, spanDirectoryPath)
    } finally {
      measurement.close()
      FileUtil.cleanDirectory(spanDirectoryPath)
    }
  }

  def publishSpan()(implicit measurements: Measurements): String = {
    val name = "short-test"
    val traceId = TraceId(RandomUtil.randomAlphaNum(3))
    val span = Span.startNoParent(name, traceId)
    span.complete()
    name
  }

  def publishGauge()(implicit measurements: Measurements): String = {
    val name = "gauge-test"
    val traceId = TraceId(RandomUtil.randomAlphaNum(3))
    implicit val span = Span.prepareRoot(name, traceId)
    Gauged[Long](name, 11)
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
    withMeasurements { (measurements, directory) =>
      val spanName = publishSpan()(measurements)
      measurements.flush()

      val lines = Files.readAllLines(directory.resolve("spans.tsv"), UTF_8).asScala
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


  test("publish a gauge to a .tsv file") {
    withMeasurements { (measurements, directory) =>
      val gaugeName = publishGauge()(measurements)
      measurements.flush()

      val lines = Files.readAllLines(directory.resolve("gauged.tsv"), UTF_8).asScala
      lines.length shouldBe 2
      lines.head shouldBe "name\ttraceId\ttime\tvalue"
      val Array(name, traceIdString, timeString, valueString) = lines(1).split("\t")
      name shouldBe gaugeName
      val time = JLong.parseLong(timeString)
      val value = JLong.parseLong(valueString)
      val timeToNow = Math.abs(time / 1000L - System.currentTimeMillis())
      timeToNow should be < 5.seconds.toMillis
      value shouldBe 11
    }
  }

}