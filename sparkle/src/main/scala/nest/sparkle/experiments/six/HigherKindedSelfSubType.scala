package nest.sparkle.experiments.six

import scala.language.higherKinds

/** A stream takes two type parameters: 
 *  
 *  K - the type of the elements
 *  StreamImpl[_] - a type constructor that constructs Stream subtypes 
 */
trait Stream[K, StreamImpl[_]] {
  streamImpl: StreamImpl[K] =>  
 
  def mapData[A] // format: OFF
    (fn: K => A)
    : Stream[A, StreamImpl] // format: ON
  
  def self:StreamImpl[K] = streamImpl
}

case class AStream[K](elems: Seq[K]) extends Stream[K, AStream] {
  override def mapData[A](fn: K => A) = AStream(elems map fn)
}

case class BStream[K](elems: Seq[K]) extends Stream[K, BStream] {
  override def mapData[A](fn: K => A):BStream[A] = BStream(elems map fn)
}

case class Stack[K, I[_]](streams: Seq[Stream[K, I]]) {
  // demonstrates that we can map the underlying data to a new type
  def mapData[A](fn: K => A): Stack[A, I] = {
    val newStreams = streams.map { _ mapData fn }
    Stack(newStreams)
  }

  // demontrates that we can map the stream to a new kind of stream,
  // (and potentially to a new type of data in the stream too)
  def mapStream[A, J[_]](fn: Stream[K, I] => Stream[A, J]): Stack[A, J] = {
    Stack(streams map fn)
  }
}

object Demo {
  val stream = AStream(Seq(1))
  val stack = Stack(Seq(stream))
  val newStack = stack.mapData(_ => false)

  val newStack2 = stack.mapStream { stream =>
    // requires use of self fowarder to get at non Stream api methods
    // this is a bit odd for the user
    BStream(stream.self.elems.map { _ => false })
  }
}

