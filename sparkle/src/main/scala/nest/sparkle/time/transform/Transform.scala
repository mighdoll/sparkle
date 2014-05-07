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

import scala.util.Try
import scala.util.control.Exception.catching

import spray.json._

import nest.sparkle.time.protocol.RangeParameters
import nest.sparkle.time.protocol.TransformParametersJson.RangeParamsFormat

case class TransformNotFound(msg: String) extends RuntimeException(msg)

/** utility functions for transform implementations */
object Transform {

  /** return typed RangeParameters from the untyped json transform parameters (in a StreamRequest message) */
  def rangeParameters[T: JsonFormat](transformParameters: JsObject): Try[RangeParameters[T]] = {
    catching(classOf[RuntimeException]).withTry {
      transformParameters.convertTo[RangeParameters[T]]
    }
  }

}

