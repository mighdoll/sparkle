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

import com.typesafe.config.Config
import akka.actor._

import nest.sparkle.test.SparkleTestConfig
import nest.sparkle.util.ConfigUtil

/** a test jig for running tests using cassandra. */
trait CassandraStoreTestConfig extends SparkleTestConfig
{
  /** override .conf file for store tests */
  override def testConfigFile: Option[String] = Some("sparkle-store-tests")

  /** the 'sparkle' level Config */
  lazy val sparkleConfig: Config = {
    ConfigUtil.configForSparkle(rootConfig)
  }

  override def configOverrides: List[(String, Any)] =
    super.configOverrides :+
    (s"${ConfigUtil.sparkleConfigName}.sparkle-store-cassandra.key-space" -> testKeySpace)

  /** subclasses should define their own keyspace so that tests don't interfere with each other  */
  def testKeySpace: String = getClass.getSimpleName

  /** recreate the database and a test column */
  def withTestDb[T](fn: CassandraReaderWriter => T): T = {
    val storeConfig = sparkleConfig.getConfig("sparkle-store-cassandra")
    val testContactHosts = storeConfig.getStringList("contact-hosts").asScala.toSeq
    CassandraStore.dropKeySpace(testContactHosts, testKeySpace)
    val notification = new WriteNotification
    val store = CassandraStore.readerWriter(sparkleConfig, notification)

    try {
      fn(store)
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

