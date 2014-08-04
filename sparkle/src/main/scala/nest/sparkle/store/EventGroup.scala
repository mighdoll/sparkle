package nest.sparkle.store

import nest.sparkle.store.Event.Events
import nest.sparkle.util.StableGroupBy._
import scala.language.existentials

/** Utility for working with groups of Events */
object EventGroup {
  /** Combine multiple column slices into a fat table rows.
    * 
    * convert from columnar form to tabular form. key,value columns are merged into wide rows
    * e.g. (key, value1), (key, value2), (key, value3) becomes (key, value1, value2, value3))
    * 
    * Each row begins with the key, followed and one Option value for each slice. The value is either
    * a Some() containing the value from the corresponding column slice, or a None if that column
    * slice didn't contain a value at that key.
    */
  def transposeSlices[T](slices: Seq[Events[T, _]]): Seq[Seq[Option[_]]] = {
    case class IndexedEvent[T](event: Event[T, _], index: Int)

    // each event, tagged with the index of the stream that it belongs too
    val indexedEvents = slices.zipWithIndex.flatMap {
      case (events, index) =>
        events.map { event => IndexedEvent(event, index) }
    }

    val columnCount = slices.length

    /** return a Seq with one slot for every source event stream. The values of the stream
      * are filled in from the IndexedEvents.
      */
    def valuesWithBlanks(indexedEvents: Traversable[IndexedEvent[T]]): Seq[Option[_]] = {
      (0 until columnCount).map { index =>
        indexedEvents.find(_.index == index).map(_.event.value)
      }
    }

    val groupedByKey = indexedEvents.stableGroupBy { indexed => indexed.event.argument }
    val groupedEvents = groupedByKey.map { // TODO DRY with IntervalSum
      case (key, indexedGroup) =>
        val values = valuesWithBlanks(indexedGroup)
        Some(key) +: values
    }
    groupedEvents.toSeq
  }

}