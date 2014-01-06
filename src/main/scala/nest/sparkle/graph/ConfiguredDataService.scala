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

package nest.sparkle.graph

import com.typesafe.config.Config
import akka.actor.ActorRefFactory
import spray.routing.Route
import scala.collection.JavaConverters._
import nest.sparkle.util.Instance

trait ConfiguredDataService extends DataService {
  def config: Config
  override def customRoutes = apis(config, registry)

  /** Return the routes from the ApiExtensions listed in the "apis" section of
    * the application.conf.  
    */
  def apis(config: Config, dataRegistry:DataRegistry)(implicit actorRefFactory: ActorRefFactory): Iterable[Route] = {
    val classes = config.getStringList("apis").asScala
    classes.map { className =>      
      Console.println(s"adding api from .conf: $className")
      val custom = Instance.byName[ApiExtension](className)(actorRefFactory, dataRegistry)
      custom.route
    }
  }
}


