package nest.sparkle.datastream

import scala.concurrent.Future
import scala.reflect.runtime.universe._


/** Asynchronous results from reducing a data stream. Includes state to propagate between reduction
  * runs. Passing state between reduction runs allows multiple streams to be stitched to together
  * into one logical reduction. */
trait ReductionResult[K,V,S] {
  def reducedStream: DataStream[K,Option[V]]
  def finishState: Future[Option[S]]
}

object ReductionResult {
  /** Asynchronous results from reducing a data stream. No state is passed between reduction runs. */
  def simple[K,V](stream:DataStream[K,Option[V]]): ReductionResult[K,V,Unit] =
    SimpleReductionResult(stream)

  def stateOnly[K:TypeTag,V:TypeTag,S](oldState:Option[S]): ReductionResult[K,V,S] =
    StateOnlyResult(oldState)

  private case class SimpleReductionResult[K,V](override val reducedStream:DataStream[K,Option[V]])
    extends ReductionResult[K,V,Unit] {
    def finishState = Future.successful(Some(Unit))
  }

  private case class StateOnlyResult[K: TypeTag, V: TypeTag,S](oldState:Option[S]) extends ReductionResult[K,V,S] {
    def reducedStream = DataStream.empty[K,Option[V]]
    def finishState = Future.successful(oldState)
  }
}
