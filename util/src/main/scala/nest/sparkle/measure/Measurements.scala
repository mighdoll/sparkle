package nest.sparkle.measure

import java.io.BufferedWriter
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, blocking}

import java.nio.charset.Charset
import java.nio.file.{Path, Files, Paths}
import java.nio.file.StandardOpenOption.{ CREATE, TRUNCATE_EXISTING }
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import nest.sparkle.util.{ ConfigUtil, Instrumented, Log, MetricsInstrumentation }
import nest.sparkle.util.BooleanOption.BooleanToOption

import nl.grons.metrics.scala.{Gauge, Timer}

/** A measurement system configured to where to publish its metrics */
class ConfiguredMeasurements(rootConfig: Config)
                            (implicit execution:ExecutionContext) extends Measurements with Log {

  val gateways = {
    val measureConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("measure")
    Seq(MeasurementToMetrics.configured(measureConfig),
      MeasurementToTsvFile.configured(measureConfig)
    ).flatten
  }

  override def publish(span: CompletedMeasurement): Unit = {
    gateways.foreach(gateway => gateway.publish(span))
  }

  override def close(): Unit = {
    gateways.foreach(_.close())
  }

  override def flush(): Unit = {
    gateways.foreach(_.flush())
  }
}

/** a measurement recording system, allows publishing duration Span measurements to various backends */
trait Measurements {
  def publish(span: CompletedMeasurement)
  def close(): Unit = {}
  def flush(): Unit = {}
}

/** a factory to create a configured Measurements gateway */
trait MeasurementGateway {

  /** subclasses should return a Measurements object if they're enabled in the .conf file */
  def configured(measureConfig: Config)
                (implicit executionContext: ExecutionContext): Option[Measurements]
}

/** a measurement system that drops measurements on the floor */
object DummyMeasurements extends Measurements {
  override def publish(span: CompletedMeasurement) {}
  override def flush() {}
}

/** a measurement system that sends measurements to a file */
class MeasurementToTsvFile
    ( directoryName: String, reportLevel: ReportLevel = Detail)
    ( implicit execution:ExecutionContext ) extends Measurements {
  @volatile var stopped = false
  val path = Paths.get(directoryName)
  val spanWriter = openOutputFile(path.resolve("spans.tsv"), "name\ttraceId\ttime\tduration\n")
  val gaugeWriter = openOutputFile(path.resolve("gauged.tsv"), "name\ttraceId\ttime\tvalue\n")

  Future {
    blocking {
      Thread.currentThread.setName("MeasurementToTsvFile-flush")
      while (!stopped) {
        Thread.sleep(1000)
        spanWriter.flush()
        gaugeWriter.flush()
      }
      spanWriter.close()
      gaugeWriter.close()
    }
  }

  private def createDirectoriesTo(path:Path): Unit = {
    val parentDir = path.getParent
    if (!Files.exists(parentDir)) {
      Files.createDirectories(parentDir)
    }
  }

  private def openOutputFile(path:Path, header:String):BufferedWriter = {
    createDirectoriesTo(path)
    val charSet = Charset.forName("UTF-8")
    val writer = Files.newBufferedWriter(path, charSet, TRUNCATE_EXISTING, CREATE)
    writer.write(header)
    writer.flush()
    writer
  }

  override def close(): Unit = { stopped = true }

  override def flush(): Unit = if (!stopped) {
    spanWriter.flush()
    gaugeWriter.flush()
  }

  def publish(measurement: CompletedMeasurement): Unit = {
    if (measurement.level.level <= reportLevel.level) {
      measurement match {
        case span: CompletedSpan => publishSpan(span)
        case gauged: Gauged[_]   => publishGauged(gauged)
      }
    }
  }

  def publishGauged(gauged: Gauged[_]): Unit = {
    val name = gauged.name
    val startMicros = gauged.start.value
    val value = gauged.value
    val traceId = gauged.traceId.value
    val csv = s"$name\t$traceId\t$startMicros\t$value\n"
    gaugeWriter.write(csv)
  }

  def publishSpan(span: CompletedSpan): Unit = {
    val name = span.name
    val startMicros = span.start.value
    val duration = span.duration.value
    val traceId = span.traceId.value
    val csv = s"$name\t$traceId\t$startMicros\t$duration\n"
    spanWriter.write(csv)
  }
}

/** a gateway that sends measurements to Coda's Metrics library */
class MeasurementToMetrics(reportLevel: ReportLevel) extends Measurements with Instrumented {

  def publish(measurement: CompletedMeasurement): Unit = {
    measurement match {
      case span:CompletedSpan => publishSpan(span)
      case gauged: Gauged[_]  => publishGauged(gauged)
    }
  }

  def publishGauged(gauged:Gauged[_]): Unit = {
    // NYI
  }

  def publishSpan(span:CompletedSpan) {
    def makeTimer(name: String): Timer = {
      metrics.timer(name)
    }

    if (span.level.level <= reportLevel.level) {
      val timers = MetricsInstrumentation.registry.getTimers.asScala
      val optTimer = timers.get(span.name).map(new Timer(_))
      val timer = optTimer.getOrElse(makeTimer(span.name))
      timer.update(span.duration.value, TimeUnit.NANOSECONDS)
    }
  }

}

/** optionally return a gateway that sends measurements to a .tsv file */
object MeasurementToTsvFile extends MeasurementGateway with Log {
  override def configured
      ( measureConfig: Config )
      ( implicit executionContext: ExecutionContext )
      : Option[Measurements] = {
    val tsvConfig = measureConfig.getConfig("tsv-gateway")
    tsvConfig.getBoolean("enable").toOption.map { _ =>
      val directory = tsvConfig.getString("directory")
      val reportLevel = GatewayReportLevel.parseReportLevel(tsvConfig)
      log.info(s"Measurements to .tsv enabled, to directory $directory  level $reportLevel")
      new MeasurementToTsvFile(directory, reportLevel)
    }
  }
}

object GatewayReportLevel extends Log {
  def parseReportLevel(gatewayConfig:Config):ReportLevel = {
    gatewayConfig.getString("level").toLowerCase match {
      case "detail" => Detail
      case "info"   => Info
      case "trace"  => Detail
      case unknown =>
        log.error(s"Gateway config error. Unknown level: '$unknown'")
        Info
    }
  }
}

/** optionally return a gateway that sends measurements to coda's Metrics library */
object MeasurementToMetrics extends MeasurementGateway with Log {
  override def configured
      ( measureConfig: Config )
      ( implicit executionContext: ExecutionContext )
      : Option[Measurements] = {
    val metricsConfig = measureConfig.getConfig("metrics-gateway")
    metricsConfig.getBoolean("enable").toOption.map { _ =>
      val reportLevel = GatewayReportLevel.parseReportLevel(metricsConfig)
      log.info(s"Measurements to Metrics gateway enabled. level $reportLevel")
      new MeasurementToMetrics(reportLevel)
    }
  }
}
