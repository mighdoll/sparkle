package nest.sparkle.measure

import nest.sparkle.util.MetricsInstrumentation
import nest.sparkle.util.Instrumented
import nl.grons.metrics.scala.Timer
import com.typesafe.config.Config
import nest.sparkle.util.Log
import scala.collection.JavaConverters._
import java.util.concurrent.TimeUnit
import nest.sparkle.util.ConfigUtil
import nest.sparkle.util.BooleanOption._
import java.nio.file.{ Files, Paths }
import java.nio.file.StandardOpenOption._
import java.nio.charset.Charset

/** A measurement system configured to where to publish its metrics */
class ConfiguredMeasurements(rootConfig: Config) extends Measurements with Log {

  val gateways = {
    val measureConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("measure")
    Seq(MeasurementToMetrics.configured(measureConfig),
      MeasurementToTsvFile.configured(measureConfig)
    ).flatten
  }

  override def publish(span: CompletedSpan): Unit = {
    gateways.foreach(gateway => gateway.publish(span))
  }
}

/** a measurement recording system, allows publishing duration Span measurements to various backends */
trait Measurements {
  def publish(span: CompletedSpan)
}

/** a factory to create a configured Measurements gateway */
trait MeasurementGateway {

  /** subclasses should return a Measurements object if they're enabled in the .conf file */
  def configured(measureConfig: Config): Option[Measurements]
}

/** a measurement system that drops measurments on the floor */
object DummyMeasurements extends Measurements {
  override def publish(span: CompletedSpan) {}
}

/** a measurment system that sends measurements to a file */
class MeasurementToTsvFile(fileName: String) extends Measurements {
  val path = Paths.get(fileName)
  val charSet = Charset.forName("UTF-8")
  val writer = Files.newBufferedWriter(Paths.get(fileName), charSet, TRUNCATE_EXISTING, CREATE)
  writer.write("name\ttraceId\ttime\tduration\n")
  writer.flush()

  def publish(span: CompletedSpan): Unit = {
    val name = span.name
    val startMillis = span.start.value / 1000L // for now in milliseconds
    val duration = span.duration.value
    val traceId = span.traceId.value
    val csv = s"$name\t$traceId\t$startMillis\t$duration\n"
    writer.write(csv)
    writer.flush() // LATER only flush every few seconds
  }

}

/** a gateway that sends measurements to Coda's Metrics library */
class MeasurementToMetrics() extends Measurements with Instrumented {

  def publish(span: CompletedSpan): Unit = {
    def makeTimer(name: String): Timer = {
      metrics.timer(name)
    }
    if (span.opsReport) {
      val timers = MetricsInstrumentation.registry.getTimers.asScala
      val optTimer = timers.get(span.name).map(new Timer(_))
      val timer = optTimer.getOrElse(makeTimer(span.name))
      timer.update(span.duration.value, TimeUnit.NANOSECONDS)
    }
  }

}

/** optionally return a gateway that sends measurements to a .tsv file */
object MeasurementToTsvFile extends MeasurementGateway {
  override def configured(measureConfig: Config): Option[Measurements] = {
    val tsvConfig = measureConfig.getConfig("tsv-gateway")
    tsvConfig.getBoolean("enable").toOption.map { _ =>
      new MeasurementToTsvFile(tsvConfig.getString("file"))
    }
  }
}

/** optionally return a gateway that sends measurements to coda's Metrics library */
object MeasurementToMetrics extends MeasurementGateway {
  override def configured(measureConfig: Config): Option[Measurements] = {
    measureConfig.getBoolean("metrics-gateway.enable").toOption.map { _ =>
      new MeasurementToMetrics()
    }
  }
}
