package nest.sparkle.time.protocol

import org.scalatest.FunSuite

import akka.actor.ActorSystem

import nest.sparkle.store.Store

/** separate instance of test service, so we can create it within a withLoadedPath block */
class TestServiceWithCassandra(override val store: Store, actorSystem: ActorSystem) extends FunSuite
    with TestDataService {
  override def actorRefFactory: ActorSystem = actorSystem
} 
