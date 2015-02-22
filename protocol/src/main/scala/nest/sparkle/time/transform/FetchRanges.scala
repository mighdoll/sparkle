package nest.sparkle.time.transform

import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._

import nest.sparkle.datastream.{TwoPartStream, SoftInterval, AsyncWithRange, DataStream}
import nest.sparkle.measure.Span
import nest.sparkle.store.Column
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.util.KindCast.castKind

object FetchRanges {

  /** read a single range from a column (or the entire column if no range is specified) */
  def fetchRange[K, V]( // format: OFF
      column: Column[K, V], 
      optRange: Option[RangeInterval[K]] = None,
      parentSpan: Option[Span]) 
      (implicit execution:ExecutionContext):AsyncWithRangeColumn[K, V] = { // format: ON

    val ongoingData = {
      val start = optRange.flatMap(_.start)
      val until = optRange.flatMap(_.until)
      val limit = optRange.flatMap(_.limit)
      column.readRange(start, until, limit, parentSpan)
    }

    implicit val keyType: TypeTag[K] = castKind(column.keyType)
    implicit val valueType: TypeTag[V] = castKind(column.valueType)

    val initial = DataStream(ongoingData.initial)
    val ongoing = DataStream(ongoingData.ongoing)
    val softInterval = optRange.map (_.softInterval)
    new AsyncWithRangeColumn(initial, ongoing, softInterval, column)
  }

}


class AsyncWithRangeColumn[K: TypeTag, V: TypeTag] // format: OFF
    ( initial: DataStream[K,V],
      ongoing: DataStream[K,V],
      requestRange: Option[SoftInterval[K]],
      val column: Column[K,V] )
    extends AsyncWithRange[K,V](initial, ongoing, requestRange)
