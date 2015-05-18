package nest.sparkle.util

import com.codahale.metrics.MetricRegistry
import nl.grons.metrics.scala.{FutureMetrics, InstrumentedBuilder, MetricBuilder, MetricName}

/**
 * Global reference to the metrics registry.
 */
object MetricsInstrumentation
{
  /**
   * The single registry used for sparkle metrics.
   */
  lazy val registry = new MetricRegistry()
}

/**
 * Convenience trait for classes that create metrics.
 */
trait Instrumented 
  extends InstrumentedBuilder with FutureMetrics
{  
  lazy val metricRegistry = MetricsInstrumentation.registry
  
  // Don't prefix metric names with the scala class.
  override lazy val metricBuilder = new MetricBuilder(MetricName(""), metricRegistry)
  override def metrics = metricBuilder
}
