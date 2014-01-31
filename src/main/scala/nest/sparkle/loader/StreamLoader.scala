package nest.sparkle.loader

import rx.lang.scala.Observable
import nest.sparkle.store.Event

trait StreamLoader {
  protected def storeEvents[T,U](columnPath:String, events:Observable[Event[T,U]]) {
   ??? 
  }
}

