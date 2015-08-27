package nest.sparkle.time.protocol

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.reflect.runtime.universe._

import org.scalatest.FunSuite

import akka.actor.ActorSystem
import nest.sparkle.store.{ReadWriteStore, Store}
import nest.sparkle.util.ConfigUtil.sparkleConfigName

/** test jig for making custom selector tests */
trait CustomSelectorTest  {

  /** a TestDataService with some tweaks for configuration, and
    * as a store (and actor system) provided by a constructor rather than inheritance
    * this is handy for testing with a store that's dynamically setup and torn down
    * in testing as the TestCassandraStore does.
    */
  class CustomSelectorService[T <: CustomSourceSelector: TypeTag]
      (override val readWriteStore: ReadWriteStore, actorSystem: ActorSystem)
      extends FunSuite with TestDataService {
    // note: defs instead of vals to deal with initialization order
    def className = typeTag[T].tpe.typeSymbol.fullName
    override def actorRefFactory: ActorSystem = actorSystem
    def selectors = Seq(className).asJava

    override def configOverrides: Seq[(String, Any)] = super.configOverrides :+ (
      s"$sparkleConfigName.custom-selectors" -> selectors)
  }

}

