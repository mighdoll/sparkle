/* Copyright 2014  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.time.protocol

import rx.lang.scala.Observable
import spray.json.JsonWriter
import spray.json._
import nest.sparkle.util.ObservableFuture._
import nest.sparkle.store.Event
import scala.concurrent.duration._
import nest.sparkle.measure.Span
import nest.sparkle.measure.TraceId
import nest.sparkle.measure.DummySpan

/** returns an observable that produces one sequence of json arrays when the provided event stream completes */
object JsonEventWriter {
  /** returns an observable that produces a one item containing a sequence of json arrays
    * when the provided Event stream completes
    */
  def fromObservableSingle[T: JsonWriter, U: JsonWriter](events: Observable[Event[T, U]]): Observable[Seq[JsArray]] = {

    events.map { eventToJsArray(_) }.toSeq // .toSeq returns an observable with a single item
  }

  /** returns an observable that produces multiple sequences of json arrays, when new data is available
    * on the incoming event stream
    */
  def fromObservableMulti[T: JsonWriter, U: JsonWriter] // format: OFF
      (events: Observable[Event[T, U]], parentSpan:Option[Span] = None)
      : Observable[Seq[JsArray]] = { // format: ON
    // TODO should pass in Observable[Seq[Event]] rather than buffer
    // TODO untested
    val buffered = events.tumbling(50.milliseconds).flatMap { _.toSeq }
    val filtered = buffered.filterNot { _.isEmpty }
    fromObservableSeq(filtered, parentSpan)
  }

  /** return an Observable containing sequence-chunks of json data from an Observable containing sequence-chunks
    * of event data.
    */
  def fromObservableSeq[T: JsonWriter, U: JsonWriter] // format: OFF
      (observed: Observable[Seq[Event[T, U]]], parentSpan:Option[Span] = None)
      : Observable[Seq[JsArray]] = { // format: ON
    val parent = parentSpan.getOrElse(DummySpan)
    observed.map { eventSeq =>
      Span.prepare("JsonEventWriter", parent).time {
        eventSeq map { event =>
          eventToJsArray(event)
        }
      }
    }
  }

  /** return the JsArray for one event */
  private def eventToJsArray[T: JsonWriter, U: JsonWriter](event: Event[T, U]): JsArray = {
    JsArray(event.argument.toJson, event.value.toJson)
  }

}

