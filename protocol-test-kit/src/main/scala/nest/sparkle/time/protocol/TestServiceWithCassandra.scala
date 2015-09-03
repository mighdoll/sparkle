package nest.sparkle.time.protocol

import org.scalatest.FunSuite

import akka.actor.ActorSystem

import nest.sparkle.store.{ReadWriteStore, Store}

/** separate instance of test service, so we can create it within a withLoadedPath block */
// TODO get rid of this, it lacks resource mgmt (e.g. a withXXX method, and is currently leaking actorSystems in the integration tests)
class TestServiceWithCassandra(val readWriteStore: ReadWriteStore, actorSystem: ActorSystem)
    extends FunSuite with TestDataService {
  override def store = readWriteStore
  override def actorRefFactory: ActorSystem = actorSystem
}
