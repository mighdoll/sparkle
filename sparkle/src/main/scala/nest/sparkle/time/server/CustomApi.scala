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

package nest.sparkle.time.server

import spray.routing.Route
import akka.actor.ActorRefFactory
import nest.sparkle.legacy.DataRegistry

/** Extensions to the REST api provided by developers using sparkle.graph as a library.
  * Developers should subclass ApiExtension and then list their subclass in the
  * 'apis' section of application.conf.  Subclasses are passed the ApiExtension parameters
  * as they are instantiated.
  */
abstract class ApiExtension(
    val actorRefFactory: ActorRefFactory, dataRegistry:DataRegistry) {
  def route: Route
}


