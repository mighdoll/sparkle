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
import scala.concurrent.{ExecutionContext, Future}
import spray.client.pipelining._
import akka.actor.ActorRefFactory
import akka.util.Timeout
import akka.actor.ActorSystem
import scala.concurrent.duration._
import nest.sparkle.util.RetryingFuture.retryingFuture

/** utility to make a request multiple times until it succeeds */
object RepeatingRequest extends Log {
  private val defaultMaxAttempts = 4

  /** http get request until it succeeds or maxAttempts is exceeded */
  def get // format: OFF
      ( uri: String, maxAttempts: Int = defaultMaxAttempts )
      ( implicit refFactory: ActorRefFactory,
        executionContext: ExecutionContext,
        futureTimeout: Timeout = 5.seconds )
      : Future[String] = { // format: ON 
    val pipeline = sendReceive

    retryingFuture(pipeline(Get(uri)), maxAttempts) map {_.entity.asString}
  }
}
