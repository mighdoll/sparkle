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

package nest.sparkle.util

/** An Iterator with headOption and tail methods.  It caches the first element internally.
  * Consider PeekableIterator if you want to peek at
  * the first element of an iterator without advancing the iterator.
  * (Also consider Stream for this purpose, which has more features.  PeekableIterator uses less
  * memory than Stream, and doesn't risk accidentally caching the entire iteration.)
  */
case class PeekableIterator[T](iterator: Iterator[T]) extends Iterator[T] {
  var returnedFirst = false
  lazy val headOption: Option[T] = { // lazy so iteration starts when the caller asks
    if (iterator.hasNext) Some(iterator.next) else None
  }

  def hasNext: Boolean = {
    iterator.hasNext || (!returnedFirst && headOption.isDefined)
  }

  def next(): T = {
    if (!returnedFirst) {
      returnedFirst = true
      headOption.get
    } else {
      iterator.next()
    }
  }

  /** Returns the underlying iterator, advanced past the first element (or advanced further
    * if next() has been called).
    */
  def tail: Iterator[T] = {
    headOption // trigger lazy iteration of first element if necessary 
    iterator
  }

}

