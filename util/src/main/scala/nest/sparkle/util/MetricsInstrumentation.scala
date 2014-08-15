package nest.sparkle.util

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.{Gauge => CHGauge}
import nl.grons.metrics.scala.{InstrumentedBuilder, MetricBuilder, MetricName}

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
  extends InstrumentedBuilder
{  
  lazy val metricRegistry = MetricsInstrumentation.registry
  
  // Don't prefix metric names with the scala class.
  private lazy val metricBuilder = new MetricBuilder(MetricName(""), metricRegistry)
  override def metrics = metricBuilder
}
