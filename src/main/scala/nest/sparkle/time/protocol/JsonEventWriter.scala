package nest.sparkle.time.protocol

import rx.lang.scala.Observable
import spray.json.JsonWriter
import spray.json._
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.store.Event

/** returns an observable that produces one sequence of json arrays when the provided event stream completes */
object JsonEventWriter {
  /** returns an observable that produces one sequence of json arrays when the provided event stream completes */
  def apply[T: JsonWriter, U: JsonWriter](events: Observable[Event[T, U]]): Observable[Seq[JsArray]] = {
    /** return the JsArray for one event */
    def eventToArray(event: Event[T, U]): JsArray = {
      JsArray(event.argument.toJson, event.value.toJson)
    }

    // LATER It would be nice to return all the available data here, but AFAICT the Observable api only gives
    // the choice of buffering by time or count, or getting all of the data.
    events.map{ eventToArray }.toSeq    
  }
}

