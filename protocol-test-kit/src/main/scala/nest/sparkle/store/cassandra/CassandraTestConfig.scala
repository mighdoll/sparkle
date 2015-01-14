package nest.sparkle.store.cassandra

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

import akka.actor._
import spray.util._

import nest.sparkle.loader.{FileLoadComplete, FilesLoader, LoadComplete, ReceiveLoaded}
import nest.sparkle.store.{Event, Store}
import nest.sparkle.util.Resources

/** a test jig for running tests using cassandra.
  * (In the main project to make it easy to share between tests and integration tests) */
trait CassandraTestConfig 
  extends CassandraStoreTestConfig
{

}

