package nest.sparkle.experiments.three
import scala.language.higherKinds

case class AStream[K](elems: Seq[K]) extends Stream[K] {
  override type StreamSubType[A] = AStream[A]

  override def mapData[A](fn: K => A): AStream[A] = {
    AStream(elems map fn)
  }
}

// let's try a type member to encode the subtype of Stream
trait Stream[K] {
  // as we'll see, this isn't quite what we want.
  // this type is path-dependent
  type StreamSubType[A] <: Stream[A]

  def mapData[A](fn: K => A): StreamSubType[A]
}

trait StreamKind[I[K] <: Stream[K]]

case class Stack[K, S[K] <: Stream[K]: StreamKind](streams: Seq[S[K]]) {

  def mapData[A](fn: K => A): Stack[A, S] = {
    val newStreams: Seq[S[A]] =
      streams.map { stream =>
        val newStream: S[K]#StreamSubType[A] = stream.mapData(fn)
        newStream.asInstanceOf[S[A]] // didn't quite work. we still have to cast
      }
    Stack(newStreams)
  }
}

