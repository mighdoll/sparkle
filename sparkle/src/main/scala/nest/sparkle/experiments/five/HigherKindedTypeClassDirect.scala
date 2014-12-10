package nest.sparkle.experiments.five

import scala.language.higherKinds

// can we define Stream this way? the idea is to get rid of the proxying layer
trait Stream[K, Impl[_]] {
  def mapData[A] // format: OFF
    (fn: K => A)
    : Impl[A] // format: ON
}

// certainly we can implement this way
case class AStream[K](elems: Seq[K]) extends Stream[K, AStream] {
  override def mapData[A](fn: K => A) = AStream(elems map fn)
}

// but how do we define the Stack?

// can't use context bounds because  we have two type parameters:
//   case class Stack[K, S[_]: Stream]() // won't compile

// but with a separate implicit it'll compile .
case class Stack[K, S[_]](streams: Seq[S[K]])(implicit streamImpl: Stream[K, S]) {

  def mapData[A](fn: K => A): Stack[A, S] = {
    val newStreams =
      streams.map { stream =>
        // but this doesn't make sense, we need to mapData on stream, not the streamImpl
        streamImpl.mapData(fn)
      }
    ???
  }
}


