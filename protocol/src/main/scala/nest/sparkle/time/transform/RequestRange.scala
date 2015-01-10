package nest.sparkle.time.transform

import nest.sparkle.time.protocol.RangeInterval


/** A TwoPartStream variant that carries the requested data range 
 *  e.g. the time range from a protocol request */
trait RequestRange[K] {
  def requestRange:Option[RangeInterval[K]]
}