package nest.sparkle.util

import com.codahale.metrics.MetricRegistry
import nl.grons.metrics.scala.InstrumentedBuilder

/**
 * Global reference to the metrics registry.
 */
object MetricsInstrumentation
{
  /**
   * The single registry used for sparkle metrics.
   */
  val metricRegistry = new MetricRegistry()
}

trait Instrumented 
  extends InstrumentedBuilder
{
  /**
   * This name is required by InstrumentedBuilder.
   */
  val metricRegistry = MetricsInstrumentation.metricRegistry
}
