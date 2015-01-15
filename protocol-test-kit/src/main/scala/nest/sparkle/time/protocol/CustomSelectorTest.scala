package nest.sparkle.time.protocol

import scala.collection.JavaConverters._
import scala.concurrent.{ Future, Promise }
import org.scalatest.FunSuite
import akka.actor.ActorSystem
import spray.util.pimpFuture
import nest.sparkle.loader.{ FileLoadComplete, FilesLoader, ReceiveLoaded }
import nest.sparkle.store.Store
import nest.sparkle.util.Resources
import scala.reflect.runtime.universe._
import nest.sparkle.loader.FileLoadComplete
import scala.reflect.runtime.universe._
import nest.sparkle.util.ConfigUtil.sparkleConfigName

/** test jig for making custom selector tests */
trait CustomSelectorTest  {

  /** a TestDataService with some tweaks for configuration, and
    * as a store (and actor system) provided by a constructor rather than inheritance
    * this is handy for testing with a store that's dynamically setup and torn down
    * in testing as the TestCassandraStore does.
    */
  class CustomSelectorService[T <: CustomSourceSelector: TypeTag](override val store: Store, actorSystem: ActorSystem)
      extends FunSuite with TestDataService {
    // note: defs instead of vals to deal with initialization order
    def className = typeTag[T].tpe.typeSymbol.fullName
    override def actorRefFactory: ActorSystem = actorSystem
    def selectors = Seq(className).asJava

    override def configOverrides: List[(String, Any)] = super.configOverrides :+ (
      s"$sparkleConfigName.custom-selectors" -> selectors)
  }

}

