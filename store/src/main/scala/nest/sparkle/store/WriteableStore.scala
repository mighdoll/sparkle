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

import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.{Failure, Success}
import nest.sparkle.datastream.DataStream
import nest.sparkle.store.cassandra.CanSerialize
import nest.sparkle.store.cassandra.WriteableColumn

/** A writeable interface to a database of Columns */
trait WriteableStore {
  /** Return an interface that supports writing to a column identified by columnPath. */
  // LATER generalize this to non cassandra typeclasses
  def writeableColumn[T: CanSerialize, U: CanSerialize]
      ( columnPath: String ): Future[WriteableColumn[T, U]]

  /** to notify listeners about writes made to the store */
  def writeNotifier: WriteNotifier

  /**
   * Format the store.
   *
   * This erases any existing data in the store. The store is in the same state
   * as if it was just created.
   */
  def format(): Unit

  /** write a data stream to the store */
  def writeStream[K: CanSerialize, V: CanSerialize]
      ( dataStream:DataStream[K, V], columnPath:String )
      ( implicit executionContext: ExecutionContext ): Future[Unit] = {

    writeableColumn[K, V](columnPath).flatMap { column =>
      val done = Promise[Unit]()
      dataStream.data.doOnEach { dataArray =>
        column.writeData(dataArray)
      }.doOnCompleted {
        done.complete(Success(Unit))
      }.doOnError {err =>
        done.complete(Failure(err))
      }.subscribe()

      done.future
    }
  }

}
