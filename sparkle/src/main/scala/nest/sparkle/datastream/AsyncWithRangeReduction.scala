package nest.sparkle.datastream

import scala.reflect.runtime.universe._

class AsyncWithRangeReduction[K: TypeTag, V: TypeTag] // format: OFF
    ( initial: DataStream[K,V],
      ongoing: DataStream[K,V],
      requestRange: Option[SoftInterval[K]],
      reductionGrouping: ReductionGrouping )
    extends AsyncWithRange[K,V](initial, ongoing, requestRange) { // format: ON


}
