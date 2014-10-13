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

package nest.sparkle.time.transform

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ ExecutionContext, Future }
import scala.reflect.runtime.universe._
import scala.util.control.Exception.catching
import rx.lang.scala.Observable
import nest.sparkle.store.{ Column, Event }
import nest.sparkle.store.OngoingEvents
import nest.sparkle.time.protocol.RangeInterval
import nest.sparkle.time.transform.ItemUtil.datedToString

/** The data transformations specified by protocol requests work on
  * data that comes from columns in the Store. Transforms may operate on
  * multiple slices from multiple columns. The slices of data are
  * organized into a heirarchical set of containers as follows: // format: OFF
  *
  * ItemSet - a collection of groups
  *   ItemGroup - data from a group of columns
  *     ItemStack - data from one or more slices of a single column 
  *       ItemStream - data from a single slice from a single column
  *         initial - all data available the time of the request
  *         ongoing - items arriving after the request (normally only for open ended ranges)
  * 
  * Conventions for type parameter letters:
  *  K - key type
  *  V - value type
  *  I - Item type
  *  S - ItemStream type        
  *  T - ItemStack type
  *  G - ItemGroup type
  *  
  * // format: ON
  */

case class IntervalAndEvents[K, V](interval: Option[RangeInterval[K]], events: OngoingEvents[K, V])

/** a single stream of data */
abstract class ItemStream[K:TypeTag, V:TypeTag, I] {
  def keyType = typeTag[K]
  def valueType = typeTag[V]
}

/** several streams from the same underlying column, typically from different ranges */
class ItemStack[K, V, I, S <: ItemStream[K, V, I]] // format: OFF
  (val streams:Seq[S]) // format: ON

/** a named collection of ItemStacks */
class ItemGroup[K, V, I, S <: ItemStream[K, V, I], T <: ItemStack[K, V, I, S]](  // format: OFF
    val stacks: Seq[T], 
    val name: Option[String]) // format: ON

/** multiple groups (e.g. from a protocol request that specified multiple groups) */
class ItemGroupSet[ // format: OFF
    K,  
    V,  
    I, 
    S <: ItemStream[K,V,I], 
    T <: ItemStack[K,V,I,S], 
    G <: ItemGroup[K,V,I,S,T]
  ] (val groups: Seq[G]) { // format: ON

  /** Print the contents of this ItemGroupSet on the debug console.
   *  Note that this will subscribe to Observable streams */
  def debugPrint()(implicit execution: ExecutionContext) = {
    for {
      group <- groups
      stack <- group.stacks
      stream <- stack.streams.headOption
    } {
      stream match {
        case async: AsyncItemStream[K, V, I] =>
          printAsyncItems(async.initial)
        case buffered: BufferedItemStream[_, _, _] =>
          buffered.initialEntire.foreach { initial =>
            printAsyncItems(Observable.from(initial))
          }
      }
    }

    /** Print the items in the Observable.
     *  Assumes that the items are keyed by timestamp */
    def printAsyncItems(items: Observable[I], name: String = "column") {
      val saved = ArrayBuffer[String]()

      /** buffer up all the lines to print, so that column contents are 
       *  printed all at once (not interleaved with other columns) */
      def saveItem(item: I) {
        item match {
          case simple: Event[_, _] =>
            saved += datedToString(simple)
          case _ => ???
        }
      }

      items
        .doOnEach(item => saveItem(item))
        .doOnCompleted {
          val together = saved.mkString(" ", "\n ", "")
          println(s"$name\n$together")
        }
        .subscribe()
    }
  }
}

