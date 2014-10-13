package nest.sparkle.time.transform

import nest.sparkle.store.Event
import nest.sparkle.store.EventGroup.OptionRow
import rx.lang.scala.Observable
import ItemStreamTypes._
import scala.reflect.runtime.universe._
import scala.concurrent.Future
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.util.ObservableFuture._

/** Shortcut type signatures for ItemGroupSet, ItemStream, etc.
  */
object ItemStreamTypes {
  type RawItem[K] = Event[K, Any]
  type RawItemStream[K] = RangedAsyncStream[K, Any, RawItem[K]]
  type RawItemStack[K] = RangedAsyncStack[K, Any, RawItem[K]]
  type RawItemGroup[K] = RangedAsyncGroup[K, Any, RawItem[K], RawItemStack[K]]
  type RawItemGroupSet[K] = ItemGroupSet[K, Any, RawItem[K], RawItemStream[K], RawItemStack[K], RawItemGroup[K]]

  type RawRangedStream[K, V] = RangedAsyncStream[K, V, Event[K, V]]
  type RawRangedStack[K, V] = RangedAsyncStack[K, V, Event[K, V]]
  type RawRangedGroup[K, V] = RangedAsyncGroup[K, V, Event[K, V], RawRangedStack[K, V]]
  type RawRangedSet[K, V] = ItemGroupSet[K, V, Event[K, V], RawRangedStream[K, V], RawRangedStack[K, V], RawRangedGroup[K, V]]

  type BufferedRawItemStream[K, V] = BufferedItemStream[K, V, Event[K, V]]
  type BufferedRawItemStack[K, V] = BufferedItemStack[K, V, Event[K, V], BufferedRawItemStream[K, V]]
  type BufferedRawItemGroup[K, V] = BufferedItemGroup[K, V, Event[K, V], BufferedRawItemStream[K, V]]
  type BufferedRawItemSet[K, V] = ItemGroupSet[K, V, Event[K, V], BufferedRawItemStream[K, V], BufferedRawItemStack[K, V], BufferedRawItemGroup[K, V]]

  type BufferedOptionRowStream[K, V] = BufferedItemStream[K, V, OptionRow[K, V]]
  type BufferedOptionRowStack[K, V] = BufferedItemStack[K, V, OptionRow[K, V], BufferedOptionRowStream[K, V]]
  type BufferedOptionRowGroup[K, V] = BufferedItemGroup[K, V, OptionRow[K, V], BufferedOptionRowStream[K, V]]
  type BufferedOptionRowSet[K, V] = ItemGroupSet[K, V, OptionRow[K, V], BufferedOptionRowStream[K, V], BufferedOptionRowStack[K, V], BufferedOptionRowGroup[K, V]]

  type BufferedMultiValueStream[K, V] = BufferedItemStream[K, V, MultiValue[K, V]]
  type BufferedMultiValueStack[K, V] = BufferedItemStack[K, V, MultiValue[K, V], BufferedMultiValueStream[K, V]]
  type BufferedMultiValueGroup[K, V] = BufferedItemGroup[K, V, MultiValue[K, V], BufferedMultiValueStream[K, V]]
  type BufferedMultiValueSet[K, V] = ItemGroupSet[K, V, MultiValue[K, V], BufferedMultiValueStream[K, V], BufferedMultiValueStack[K, V], BufferedMultiValueGroup[K, V]]

  type RangedIntervalStream[K] = RangedAsyncStream[K, K, IntervalItem[K]]
  type RangedIntervalStack[K] = RangedAsyncStack[K, K, IntervalItem[K]]
  type RangedIntervalGroup[K] = RangedAsyncGroup[K, K, IntervalItem[K], RangedIntervalStack[K]]
  type RangedIntervalSet[K] = ItemGroupSet[K, K, IntervalItem[K], RangedIntervalStream[K], RangedIntervalStack[K], RangedIntervalGroup[K]]

  type BufferedIntervalStream[K] = BufferedItemStream[K, K, IntervalItem[K]]
  type BufferedIntervalStack[K] = BufferedItemStack[K, K, IntervalItem[K], BufferedIntervalStream[K]]
  type BufferedIntervalGroup[K] = BufferedItemGroup[K, K, IntervalItem[K], BufferedIntervalStream[K]]
  type BufferedIntervalSet[K] = ItemGroupSet[K, K, IntervalItem[K], BufferedIntervalStream[K], BufferedIntervalStack[K], BufferedIntervalGroup[K]]

  type PeriodIntervalStack[K] = BufferedItemStack[K, K, IntervalItem[K], PeriodIntervalStream[K]]
  type PeriodIntervalGroup[K] = BufferedItemGroup[K, K, IntervalItem[K], PeriodIntervalStream[K]]
  type PeriodIntervalSet[K] = ItemGroupSet[K, K, IntervalItem[K], PeriodIntervalStream[K], PeriodIntervalStack[K], PeriodIntervalGroup[K]]
}

/** a stream of data from a given section of an underlying column */
class AsyncItemStream[K: TypeTag, V: TypeTag, I <: Event[K, V]]( // format: OFF
    /** data available at the initial request */
    val initial: Observable[I], 
    /** data available after the initial request */
    val ongoing: Observable[I]
  ) extends ItemStream[K, V, I] { // format: ON 
}

class AsyncItemStack[K, V, I <: Event[K, V], S <: AsyncItemStream[K, V, I]]( // format: OFF
    _streams:Seq[S]
  ) extends ItemStack[K,V,I,S](_streams) // format: ON

/** An AsyncItemStream along with the requested range in the column */
case class RangedAsyncStream[K: TypeTag, V: TypeTag, I <: Event[K, V]]( // format: OFF
    _initial: Observable[I],
    _ongoing: Observable[I],
    val fromRange: Option[RangeInterval[K]]
  ) extends AsyncItemStream[K, V, I](_initial, _ongoing) // format: ON

/** a stack with a request range for each stream */
class RangedAsyncStack[K, V, I <: Event[K, V]]( // format: OFF
    _streams:Seq[RangedAsyncStream[K,V,I]]
  ) extends AsyncItemStack[K,V,I,RangedAsyncStream[K,V,I]](_streams) // format: ON

/** a collection of RangedAsyncStreams, all sharing the same value type */
class RangedAsyncGroup[K, V, I <: Event[K, V], T <: RangedAsyncStack[K, V, I]]( // format: OFF
    _stacks: Seq[T],
    _name: Option[String]) 
  extends ItemGroup[K,V,I,RangedAsyncStream[K,V,I],T](_stacks, _name) // format: ON

/** a stream of data from a section of an underlying column.
  * The initially available data is delivered in a single array, rather than as a stream. Any ongoing
  * data is delivered as a stream.
  */
class BufferedItemStream[K: TypeTag, V: TypeTag, I]( // format: OFF
    val initialEntire: Future[Seq[I]],
    val ongoing: Observable[I],
    val fromRange: Option[RangeInterval[K]]
    ) extends ItemStream[K, V, I] { // format: ON
}

class BufferedItemStack[K, V, I, S <: BufferedItemStream[K, V, I]]( // format: OFF
    _streams:Seq[S])
  extends ItemStack[K,V,I,S](_streams) // format: ON

class BufferedItemGroup[K, V, I, S <: BufferedItemStream[K, V, I]]( // format: OFF
    _stacks:Seq[BufferedItemStack[K,V,I,S]],
    _name:Option[String]
    )  extends ItemGroup[K,V,I, S, BufferedItemStack[K,V,I,S]](_stacks, _name) // format: ON

/** a BufferedItemSet where the items are IntervalItem */
object BufferedIntervalSet {
  /** factory to create a BufferedIntervalSet from a RangedIntervalSet */
  def fromRangedSet[K](ranged: RangedIntervalSet[K]): BufferedIntervalSet[K] = {
    val groups =
      ranged.groups.map { group =>
        val stacks =
          group.stacks.map { stack =>
            val streams =
              stack.streams.map { stream =>
                val initial = stream.initial.toFutureSeq
                implicit val tag = stream.keyType
                new BufferedIntervalStream(initial, stream.ongoing, stream.fromRange)
              }
            new BufferedIntervalStack(streams)
          }
        new BufferedIntervalGroup(stacks, group.name)
      }
    new BufferedIntervalSet(groups)
  }
}

/** a BufferedItemStream that also includes a TimePartitions object for dividing the input into
 *  partitions based on a requested time partition (e.g. "1 month")
 */
class PeriodIntervalStream[K: TypeTag]( // format: OFF
    _initialEntire: Future[Seq[IntervalItem[K]]],
    _ongoing: Observable[IntervalItem[K]],
    _fromRange: Option[RangeInterval[K]],
    val timeParts: Option[Future[TimePartitions]]
    ) extends BufferedItemStream[K,K,IntervalItem[K]](_initialEntire, _ongoing, _fromRange) // format: ON

/** an Item with a single key and multiple values */
class MultiValue[K, V](key: K, values: Seq[V]) extends Event[K, Seq[V]](key, values)

/** an RangedAsyncSet where the Items are basic key value pairs. This type of set packages initial
 *  data from the store, before subsequent transformations. */
class FetchedGroupSet[K]( // format: OFF
    groups:Seq[RawItemGroup[K]]
  ) extends ItemGroupSet[K, Any, RawItem[K],RawItemStream[K],RawItemStack[K], RawItemGroup[K]](groups) { // format: ON

  def castValues[V] = this.asInstanceOf[RawRangedSet[K, V]]
}

