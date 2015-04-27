package nest.sparkle.store

import nest.sparkle.util.StableGroupBy._
import scala.language.existentials
import rx.lang.scala.Observable
import nest.sparkle.util.ObservableUtil

//import spray.json.JsonFormat
//import nest.sparkle.time.protocol.KeyValueType


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
  def transposeSlices[T](slices: Seq[Events[T, _]]): Seq[Seq[Option[Any]]] = {
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

    val groupedByKey = indexedEvents.stableGroupBy { indexed => indexed.event.key }
    val groupedEvents = groupedByKey.map {
      case (key, indexedGroup) =>
        val values = valuesWithBlanks(indexedGroup)
        Some(key) +: values
    }
    groupedEvents.toSeq
  }

  /** a slice of key-value events (typically from one column) */
  type Events[T, U] = Seq[Event[T, U]]

  /** a row containing one key and man values */
  type KeyedRow[T, U] = Seq[Event[T, Seq[U]]]

  /** a row containing one key and many optional values */
  type OptionRow[T, U] = Event[T, Seq[Option[U]]]

  /** a slice of OptionRows */
  type OptionRows[T, U] = Seq[OptionRow[T, U]]

  /** Convert N key-value column streams into one stream containing rows with N values.  */
  def groupByKey[T, U](eventStreams: Seq[Observable[Events[T, U]]]): Observable[OptionRows[T, U]] = {
    val headsAndTails = eventStreams.map { stream => ObservableUtil.headTail(stream) }
    // heads has a Seq of Observables containing one Events item each. 
    // (tails contains ongoing data, not yet handled by this transform. TODO handle ongoing data too)
    val (heads, tails) = headsAndTails.unzip

    // Create a single Observable containing the initial results from all columns (each column's result in its own Seq)  
    val headsTogether: Observable[Seq[Event[T, U]]] = heads.reduceLeft { (a, b) => a ++ b } // SCALA make it more clear from the types that heads contains one item (Future?)

    val tabular =
      headsTogether.toSeq.map { initialEvents => // initial Events from all summarized columns

        // convert to tabular form
        val rows = EventGroup.transposeSlices(initialEvents)
        rows.map { row =>
          val key = row.head.get.asInstanceOf[T]
          val values = row.tail.asInstanceOf[Seq[Option[U]]]
          Event(key, values)
        }
      }

    tabular
  }

  /** convert rows with optional values into rows with zeros for None */
  def blanksToZeros[T, U: Numeric](rowBlocks: Observable[OptionRows[T, U]]) // format: OFF
      : Observable[KeyedRow[T, U]] = { // format: ON
    val zero = implicitly[Numeric[U]].zero

    rowBlocks map { rows =>
      rows.map { row =>
        val optValues: Seq[Option[U]] = row.value
        val zeroedValues = optValues.map(_.getOrElse(zero))
        Event(row.key, zeroedValues)
      }
    }
  }
/*
  def rowsToJson[T: JsonFormat, U: JsonFormat](rowBlocks: Observable[KeyedRow[T, U]]): JsonDataStream = {
    JsonDataStream(
      dataStream = JsonEventWriter.fromObservableSeq(rowBlocks),
      streamType = KeyValueType
    )
  }
*/
}