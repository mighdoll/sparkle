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

package nest.sparkle.store.cassandra

import scala.collection.JavaConverters._

import com.typesafe.config.{Config, ConfigFactory}

import akka.actor._
import nest.sparkle.util.{ConfigUtil, ConfigureLogback}

/** a test jig for running tests using cassandra.
 *  (In the main project to make it easy to share between tests and integration tests) */
trait CassandraTestConfig {
  /** subclasses should define their own keyspace so that tests don't interfere with each other  */
  def testKeySpace:String

  /** subclasses may override to modify the Config for particular tests */
  def configOverrides:List[(String,String)] = List()

  /** the outermost Config object */
  lazy val rootConfig:Config = {
    val root = ConfigFactory.load()
    val overrides = ("sparkle-time-server.sparkle-store-cassandra.key-space" -> testKeySpace) :: configOverrides
    val modifiedRoot = ConfigUtil.modifiedConfig(root, overrides:_*)
    val sparkleConfig = modifiedRoot.getConfig("sparkle-time-server")
    ConfigureLogback.configureLogging(sparkleConfig)
    modifiedRoot
  }

  /** the 'sparkle' level Config, one down from the outermost */
  lazy val sparkleConfig = {
    rootConfig.getConfig("sparkle-time-server")
  }

  /** recreate the database and a test column */
  def withTestDb[T](fn: CassandraStore => T): T = {
    val storeConfig = sparkleConfig.getConfig("sparkle-store-cassandra")
    val testContactHosts = storeConfig.getStringList("contact-hosts").asScala.toSeq
    CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
    val store = CassandraStore(sparkleConfig)

    try {
      fn (store)
    } finally {
      store.close()
//      CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
    }
  }

  /** run a function within a test actor system */
  def withTestActors[T](fn: ActorSystem => T): T = {
    val system = ActorSystem("test-config")
    try {
      fn(system)
    } finally {
      system.shutdown()
    }
  }

}
