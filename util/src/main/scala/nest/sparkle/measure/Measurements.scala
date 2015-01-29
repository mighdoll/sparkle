package nest.sparkle.measure

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, blocking}

import java.nio.charset.Charset
import java.nio.file.{ Files, Paths }
import java.nio.file.StandardOpenOption.{ CREATE, TRUNCATE_EXISTING }
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import nest.sparkle.util.{ ConfigUtil, Instrumented, Log, MetricsInstrumentation }
import nest.sparkle.util.BooleanOption.BooleanToOption

import nl.grons.metrics.scala.Timer

/** A measurement system configured to where to publish its metrics */
class ConfiguredMeasurements(rootConfig: Config)
                            (implicit execution:ExecutionContext) extends Measurements with Log {

  val gateways = {
    val measureConfig = ConfigUtil.configForSparkle(rootConfig).getConfig("measure")
    Seq(MeasurementToMetrics.configured(measureConfig),
      MeasurementToTsvFile.configured(measureConfig)
    ).flatten
  }

  override def publish(span: CompletedSpan): Unit = {
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
  def publish(span: CompletedSpan)
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
  override def publish(span: CompletedSpan) {}
  override def flush() {}
}

/** a measurement system that sends measurements to a file */
class MeasurementToTsvFile
    ( fileName: String )
    ( implicit execution:ExecutionContext ) extends Measurements {
  @volatile var stopped = false
  val path = Paths.get(fileName)
  val parentDir = path.getParent
  if (!Files.exists(parentDir)) {
    Files.createDirectories(parentDir)
  }
  val charSet = Charset.forName("UTF-8")
  val writer = Files.newBufferedWriter(path, charSet, TRUNCATE_EXISTING, CREATE)
  writer.write("name\ttraceId\ttime\tduration\n")
  writer.flush()

  Future {
    blocking {
      Thread.currentThread.setName("MeasurementToTsvFile-flush")
      while (!stopped) {
        Thread.sleep(1000)
        writer.flush()
      }
      writer.close()
    }
  }

  override def close(): Unit = { stopped = true }
  override def flush(): Unit = if (!stopped) writer.flush()

  def publish(span: CompletedSpan): Unit = {
    val name = span.name
    val startMicros = span.start.value
    val duration = span.duration.value
    val traceId = span.traceId.value
    val csv = s"$name\t$traceId\t$startMicros\t$duration\n"
    writer.write(csv)
  }
}

/** a gateway that sends measurements to Coda's Metrics library */
class MeasurementToMetrics(reportLevel: ReportLevel) extends Measurements with Instrumented {

  def publish(span: CompletedSpan): Unit = {
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
      val file = tsvConfig.getString("file")
      log.info(s"Measurements to .tsv enabled, to file $file")
      new MeasurementToTsvFile(file)
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
      log.info("Measurements to Metrics gateway enabled")
      val reportLevel =
        metricsConfig.getString("level").toLowerCase match {
          case "detail" => Detail
          case "info"   => Info
          case "trace"  => Detail
          case unknown =>
            log.error(s"MeasurementToMetrics confg error. Unknown level: '$unknown'")
            Info

        }
      new MeasurementToMetrics(reportLevel)
    }
  }
}
