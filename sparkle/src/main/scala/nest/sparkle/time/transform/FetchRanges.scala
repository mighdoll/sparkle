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

object FetchRanges {

  /** read a single range from a column (or the entire column if no range is specified) */
  def fetchRange[K, V]( // format: OFF
      column: Column[K, V], 
      optRange: Option[RangeInterval[K]] = None,
      parentSpan: Option[Span]) 
      (implicit execution:ExecutionContext):AsyncWithRequestRange[K, V] = { // format: ON

    val ongoingEvents = {
      val start = optRange.flatMap(_.start)
      val until = optRange.flatMap(_.until)
      val limit = optRange.flatMap(_.limit)
      column.readRange(start, until, limit, parentSpan)
    }

    val keyType: TypeTag[K] = castKind(column.keyType)
    val valueType: TypeTag[V] = castKind(column.valueType)
    val ongoingData = OngoingDataShim.fromOngoingEvents(ongoingEvents)(keyType, valueType)
    AsyncWithRequestRange(ongoingData, optRange)
  }

}
