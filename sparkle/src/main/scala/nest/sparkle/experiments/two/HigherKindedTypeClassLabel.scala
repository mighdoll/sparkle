package nest.sparkle.experiments.two

case class AImpl[K](elems: Seq[K]) extends Stream[K] {
  override def mapData[A](fn: K => A): AImpl[A] = {
    AImpl(elems map fn)
  }
}

trait Stream[K] {
  def mapData[A](fn: K => A): Stream[A]
}

// we need to describe which subtype of Stream we have, independent of K
// ..sounds like a use case for higher kinds
import scala.language.higherKinds

trait ImplKind[I[_] <: Stream[_]]

// S now encodes the implementation type, that's good!
case class Stack[K, S[X] <: Stream[X]: ImplKind](streams: Seq[S[K]]) {

  def mapData[A](fn: K => A): Stack[A, S] = {
    val newStreams: Seq[S[A]] =
      streams.map { stream:S[K] =>
        // close, but no cigar. stream.mapData returns an Stream[A], not the S[A] we want 
        val newStream: Stream[A] = stream.mapData(fn) 
        // we can cast, but it's unsafe.
        // an implementation of mapData might return the wrong type and we'd not catch it 'til runtime
        newStream.asInstanceOf[S[A]]
      }
    Stack(newStreams)
  }
}

// the problem is that our mapData function returns the trait type, rather
// than the appropriate subtype
