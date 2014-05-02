/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

package nest.sparkle.store

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import rx.lang.scala.Observable
import scala.reflect.runtime.universe._

/** a readable column of data that supports simple range queries.  */
trait Column[T, U] {
  /** name of this column */
  def name: String

  def keyType: TypeTag[_]
  def valueType: TypeTag[_]

  /** read a slice of events from the column, inclusive of the start and end values.
   *  If start is missing, read from the first element in the column.  If end is missing
   *  read from the last element in the column.  */      // format: OFF
  def readRange(start:Option[T] = None, end:Option[T] = None)
      (implicit execution: ExecutionContext): Observable[Event[T,U]] // format: ON

  /** read a range of events from the column, in reverse order,
   *  starting with the first event before the start value (exclusive of the start value) */      // format: OFF
  def readBefore(start:T, maxResults:Long = Long.MaxValue)
      (implicit execution: ExecutionContext): Observable[Event[T,U]] // format: ON

  /** read a range of events from the column if forwards order
   *  starting with the first event after the start value (exclusive of the start value) */      // format: OFF
  def readAfter(start:T, maxResults:Long = Long.MaxValue)
      (implicit execution: ExecutionContext): Observable[Event[T,U]] // format: ON

  // LATER add authorization hook, to validate permission to read a range
}

// LATER consider making value more flexible:
// . a sequence?  values:Seq[V].  e.g. if we want to store:  [1:[123, 134], 2:[100]]
// . an hlist?  e.g. if we want to store a csv file with multiple separately typed columns per row
// . possibly a typeclass that covers all single, sequence, and typed list cases?
// LATER name this something else: Item?  Datum?
/** an single item in the datastore, e.g. a timestamp and value */
case class Event[T, V](argument: T, value: V) 
