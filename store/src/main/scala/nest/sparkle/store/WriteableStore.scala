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

package nest.sparkle.store

import scala.concurrent.{ExecutionContext, Future}

import nest.sparkle.store.cassandra.CanSerialize
import nest.sparkle.store.cassandra.WriteableColumn

/** A writeable interface to a database of Columns */
trait WriteableStore {
  /** Return an interface that supports writing to a column identified by columnPath. */
  // TODO generalize this to non cassandra typeclasses
  def writeableColumn[T: CanSerialize, U: CanSerialize](columnPath: String): Future[WriteableColumn[T, U]]

  /**
   * Format the store.
   *
   * This erases any existing data in the store. The store is in the same state
   * as if it was just created.
   */
  def format(): Unit
  
//  /** 
//   * This must be called before queueing any entries. 
//   * The current thread may be blocked if the lock can't be acquired.
//   * While the lock is held writing to the store is blocked.
//   */
//  def acquireEnqueueLock(): Unit
//
//  /**
//   * Release the previously acquired enqueue lock.
//   * After releasing the lock writing to the store may occur.
//   */
//  def releaseEnqueueLock(): Unit
//  
//  /** Add events to the store's table buffers */
//  def enqueue[T: CanSerialize, U: CanSerialize](columnPath: String, items:Iterable[Event[T,U]])
//      (implicit executionContext: ExecutionContext): Unit
//  
//  /** Flush buffered events to storage */
//  def flush(): Unit
}
