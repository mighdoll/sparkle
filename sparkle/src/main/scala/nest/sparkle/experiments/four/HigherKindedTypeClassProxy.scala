package nest.sparkle.experiments.four

import scala.language.higherKinds

/* The approach here is to implement the Stream functions in a separate 
 * proxy objects rather than making them methods of the Stream implementations. 
 */

// we still have a higher kinded type S for the type of Stream
case class Stack[K, S[_]: Stream](streams: Seq[S[K]]) {
  val streamProxy = implicitly[Stream[S]]

  def mapData[A](fn: K => A): Stack[A, S] = {
    val newStreams: Seq[S[A]] =
      streams.map { stream =>
        streamProxy.mapData(stream)(fn) // use the proxy, luke. It returns the correct type S
      }
    Stack(newStreams)
  }

  def mapStream[A, J[_]: Stream](fn: S[K] => J[A]): Stack[A, J] = {
    Stack(streams map fn)
  }
}

trait Stream[Impl[_]] {
  def mapData[K, A] // format: OFF
      (impl: Impl[K])  // we take an additional parameter 
      (fn: K => A)
      : Impl[A] // and now we can return the appropriate stream implementation
  
}

// the AStream doesn't implement the Stream methods, those are deferred to the proxy
case class AStream[K](elems: Seq[K])

case class BStream[K](elems: Seq[K])

object AStream {
  // the functions on Stream instances are now implemented in these
  // proxy interfaces instead of the data record
  implicit object aStream extends Stream[AStream] {
    override def mapData[K, A] // format: OFF
        (impl: AStream[K])
        (fn: K => A)
        : AStream[A] = { // format: ON
      AStream(impl.elems map fn)
    }
  }
}

object BStream {
  implicit object bStream extends Stream[BStream] {
    override def mapData[K, A] // format: OFF
        (impl: BStream[K])
        (fn: K => A)
        : BStream[A] = { // format: ON
      BStream(impl.elems map fn)
    }
  }
}

object Demo {
  val stream = AStream(Seq(1))
  val stack = Stack(Seq(stream))
  val newStack = stack.mapData { _ => false }

  val newStack2 = stack.mapStream { stream =>
    BStream(stream.elems.map { _ => false })
  }
}

