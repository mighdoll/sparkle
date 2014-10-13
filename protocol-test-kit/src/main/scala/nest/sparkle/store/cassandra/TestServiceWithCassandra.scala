package nest.sparkle.store.cassandra

import akka.actor.ActorSystem
import org.scalatest.FunSuite
import nest.sparkle.store.Store
import nest.sparkle.time.protocol.TestDataService

/** separate instance of test service, so we can create it within a withLoadedPath block */
class TestServiceWithCassandra(override val store: Store, actorSystem: ActorSystem) extends FunSuite
    with TestDataService {
  override def actorRefFactory: ActorSystem = actorSystem
} 
