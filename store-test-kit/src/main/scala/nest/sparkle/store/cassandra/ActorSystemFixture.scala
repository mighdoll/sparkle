package nest.sparkle.store.cassandra

import akka.actor.ActorSystem

import nest.sparkle.util.RandomUtil._

object ActorSystemFixture {
  /** run a function within a test actor system */
  def withTestActors[T](name:String = "test-actors")(fn: ActorSystem => T): T = {
    val system = ActorSystem(name + "-" + randomAlphaNum(3))
    try {
      fn(system)
    } finally {
      system.shutdown()
    }
  }

}
