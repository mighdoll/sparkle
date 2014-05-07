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

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.config.Config

import spray.json._

import nest.sparkle.store.{Column, Store}

/** Trait for developer provided subclasses that identify source columns
  * in response to protocol StreamRequests
  */
trait CustomSourceSelector {
  /** a name used to match the `source` field in the StreamRequest message to identify when
    * this record should be used. The namespace of `source` selector strings is shared
    * across all requests, so subclasses are advised to override this with a unique name.
    */
  def name: String = this.getClass.getSimpleName

  /** Parse the `selectorParameters` field in the RequestMessage and produce one or
    * more Columns that the transforms can operate on.  If the selectorParameters
    * are invalid, custom selectors should return a failed future containing a
    * MalformedSourceSelector exception.
    */
  def selectColumns(selectorParameters: JsObject)(implicit execution: ExecutionContext) // format: OFF
      :Seq[Future[Column[_,_]]] // format: ON
}

/** Implementors of CustomSourceSelector should have a constructor that takes a Config and a Store
  * and must extend CustomSourceSelector
  */
class ExampleCustomSelector(rootConfig: Config, store: Store) extends CustomSourceSelector {
  override val name = "MyStuff.MySelectorName"

  def selectColumns(selectorParameters: JsObject)(implicit execution: ExecutionContext) // format: OFF
      :Seq[Future[Column[_,_]]] = ??? // format: ON
}

