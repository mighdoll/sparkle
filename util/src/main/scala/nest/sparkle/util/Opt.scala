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
import scala.language.implicitConversions

/** A wrapper for an optional parameter to a function or method.
  * Implicit conversions allow the caller to use a raw value _or_ an Option for the parameter.  The
  * function implementation uses the parameter as an option.
  */
case class Opt[T] (val option: Option[T]) extends AnyVal

// from: http://stackoverflow.com/questions/4199393/are-options-and-named-default-arguments-like-oil-and-water-in-a-scala-api
object Opt {
  implicit def any2opt[T](t: T): Opt[T] = new Opt(Option(t)) // NOT Some(t)
  implicit def option2opt[T](o: Option[T]): Opt[T] = new Opt(o)
  implicit def opt2option[T](o: Opt[T]): Option[T] = o.option
}


// LATER make this a value class in scala 2.10
