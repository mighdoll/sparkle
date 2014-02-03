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

package nest.sparkle.legacy

/** a single time-stamped data point */
case class Datum(time: Long, value: Double)

object Datum {
  implicit object ValueOrdering extends Ordering[Datum] {
    def compare(a:Datum, b:Datum):Int = 
      if (a.value > b.value) 1
      else if (a.value < b.value) -1
      else 0
  }
  
} 


case class TypedDatum[T](time:Long, value:T)