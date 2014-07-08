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

object Repeating {
  /** repeat a test function until it returns successful, up to a maxAttempts times, blocking the thread
    * for a definable period after each failure
    */
  def repeatWithDelay[T](maxAttempts: Int, delayMsec: Int = 100)(fn: => Option[T]): Option[T] = {
    def isDefinedOrDelay[T](option: Option[T])(delayMsec: Int = 100): Boolean = {
      option match {
        case Some(_) => true
        case None    => Thread.sleep(100); false
      }
    }

    // mapped iterator lazily executes fn
    val attempts = Iterator.range(0, maxAttempts).map { _ =>
      fn
    }
    val found: Option[Option[T]] = attempts.find { isDefinedOrDelay(_)(delayMsec) }
    found.flatten
  }
}
