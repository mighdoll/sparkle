package nest.sparkle.experiments.seven

import scala.language.higherKinds

// in this variant, we make explicit that our stream implementation types
// are subtypes of Stream. It's not clear this buys us anything
trait Stream[K, Impl[_] <: Stream[_, Impl]] {
  def mapData[A] // format: OFF
    (fn: K => A)
    : Stream[A, Impl] // format: ON
  def me: Impl[K]
}

case class AStream[K](elems: Seq[K]) extends Stream[K, AStream] {
  override def mapData[A](fn: K => A) = AStream(elems map fn)
  override def me = this
}

case class BStream[K](elems: Seq[K]) extends Stream[K, BStream] {
  override def mapData[A](fn: K => A) = BStream(elems map fn)
  override def me = this
}

case class Stack[K, I[_] <: Stream[_, I]](streams: Seq[Stream[K, I]]) {
  def mapData[A](fn: K => A): Stack[A, I] = {
    val newStreams = streams.map { _ mapData fn }
    Stack(newStreams)
  }

  def mapStream[A, J[_] <: Stream[_, J]](fn: Stream[K, I] => Stream[A, J]): Stack[A, J] = {
    val newStreams = streams.map { fn(_) }
    Stack(newStreams)
  }
}


object Demo {
  val stream = AStream(Seq(1))
  val stack = Stack(Seq(stream))
  val newStack = stack.mapData(_ => false)

  val newStack2 = stack.mapStream { stream =>
    BStream(stream.me.elems.map { _ => false })
  }
}
