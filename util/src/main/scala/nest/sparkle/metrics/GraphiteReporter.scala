package nest.sparkle.metrics

import java.io.Closeable
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import com.codahale.metrics.graphite.{Graphite => CHGraphite, GraphiteReporter => CHGraphiteReporter}

import nest.sparkle.util.MetricsInstrumentation

/**
 * Optional GraphiteReporter support.
 * 
 * Writes Metrics events to a graphite server port at a regular interval.
 */
object GraphiteReporter extends MetricsGraphiteReporter {
  /**
   * Build and start a Graphite reporter.
   * @param graphiteConfig graphite config object
   * @return reporter that can be closed
   */
  def start(graphiteConfig: Config): Closeable = {
    val prefix = graphiteConfig.getString("prefix")
    val host = graphiteConfig.getString("reporter.host")
    val port = graphiteConfig.getInt("reporter.port")
    val interval = graphiteConfig.getDuration("reporter.interval", TimeUnit.MINUTES)
    
    val graphite = new CHGraphite(new InetSocketAddress(host, port))
    val reporter = CHGraphiteReporter.forRegistry(MetricsInstrumentation.registry)
      .prefixedWith(prefix)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build(graphite)
    
    reporter.start(interval, TimeUnit.MINUTES)
    
    reporter
  }
}
