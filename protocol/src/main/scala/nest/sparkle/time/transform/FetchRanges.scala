package nest.sparkle.time.transform

import nest.sparkle.store.Column
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.measure.Span
import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe._
import nest.sparkle.util.KindCast._
import nest.sparkle.store.OngoingEvents
import rx.lang.scala.Observable
import nest.sparkle.store.Event
import scala.reflect.ClassTag
import nest.sparkle.core.OngoingDataShim
import nest.sparkle.datastream.{DataStream, AsyncWithRange}
import nest.sparkle.datastream.SoftInterval

object FetchRanges {

  /** read a single range from a column (or the entire column if no range is specified) */
  def fetchRange[K, V]( // format: OFF
      column: Column[K, V], 
      optRange: Option[RangeInterval[K]] = None,
      parentSpan: Option[Span]) 
      (implicit execution:ExecutionContext):AsyncWithRange[K, V] = { // format: ON

    val ongoingData = {
      val start = optRange.flatMap(_.start)
      val until = optRange.flatMap(_.until)
      val limit = optRange.flatMap(_.limit)
      column.readRangeA(start, until, limit, parentSpan)
    }

    implicit val keyType: TypeTag[K] = castKind(column.keyType)
    implicit val valueType: TypeTag[V] = castKind(column.valueType)

    val initial = DataStream(ongoingData.initial)
    val ongoing = DataStream(ongoingData.ongoing)
    val softInterval = optRange.map (_.softInterval)
    AsyncWithRange(initial, ongoing, softInterval)
  }

}
