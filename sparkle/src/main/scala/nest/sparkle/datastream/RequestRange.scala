package nest.sparkle.datastream


/** A TwoPartStream variant that carries the requested data range 
 *  e.g. the time range from a protocol request */
trait RequestRange[K] {
  def requestRange:Option[SoftInterval[K]]
}