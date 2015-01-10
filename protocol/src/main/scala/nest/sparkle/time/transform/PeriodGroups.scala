package nest.sparkle.time.transform

import nest.sparkle.util.PeriodWithZone
import nest.sparkle.time.protocol.RangeInterval

object PeriodGroups {
  /** report that all items are in the same group, using the first item's
    * key as the group key
    */
  def allInPartition[K, V](): (K, V) => K = {
    var started = false
    var firstKey = 0.asInstanceOf[K] // will not be used

    (key, value) => {
      if (!started) {
        firstKey = key
      }
      firstKey
    }
  }

  def partByPeriod[K, V] // format: OFF
      (periodWithZone: PeriodWithZone, rangeInterval:RangeInterval[K])
      : (K, V) => K = { // format: ON
    val PeriodWithZone(period, dateTimeZone) = periodWithZone
    period.toJoda
    ???
  }
}