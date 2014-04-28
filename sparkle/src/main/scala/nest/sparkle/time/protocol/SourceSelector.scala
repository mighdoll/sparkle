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

package nest.sparkle.time.protocol

import spray.json.JsValue
import scala.concurrent.Future
import spray.json.JsString
import spray.json.JsObject
import nest.sparkle.util.Exceptions.NYI
import scala.concurrent.ExecutionContext
import nest.sparkle.store.Column
import nest.sparkle.store.Store

/** return a list of columns given a set of source selectors */
object SourceSelector {

  /** return a collection of columns for the request*/
  def sourceColumns(sources: Array[JsValue], store: Store) // Format: OFF
      (implicit execution:ExecutionContext): Future[Seq[Column[_, _]]] = {  // Format: ON
    val columnFutures:Array[Future[Column[_,_]]] =
      sources.map { jsValue =>
        def errorValue:String = s"source: ${jsValue.prettyPrint}"
        jsValue match {
          case JsString(columnPath) => store.column(columnPath)
          case JsObject(fields)     => NYI("custom selector:  $errorValue")
          case _                    => throw new IllegalArgumentException(s"source: $errorValue")
        }
      }

    val futureColumns = Future.sequence(columnFutures.toSeq)

    futureColumns
  }

}
