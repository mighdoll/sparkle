package nest.sparkle.util

import scala.util.{Success, Try}

object TryUtil {

  /**
   * Returns the first Failure from the Try iterator if any, else a flattened Success Vector
   */
  def firstFailureOrElseSuccessVector[T](iter : Iterator[Try[T]]): Try[Vector[T]] = {
    var stopNextTime = false
    val tries = iter.takeWhile { x =>
      val stop = stopNextTime
      stopNextTime = x.isFailure
      !stop
    }.toVector

    tries.lastOption match {
      case Some(last) =>
        last.map { _ =>
          assert(tries.forall(_.isSuccess))
          tries.map(_.get)
        }
      case None    =>
        Success(Vector.empty)
    }
  }

}
