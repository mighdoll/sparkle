package nest.sparkle.util

import rx.lang.scala.Observable
import rx.lang.scala.Notification

/** we wrap each notification from the materialized streams in an object containing
  * a reference the stream itself and onNext reaction function
  */
case class Wrapped[T](notification: Notification[T], stream: Observable[T], reaction: T => Unit)

trait SubjectReact[T, U] { // SCALA TODO hlist me!
  def streamA: Observable[T]
  def reactA(t: T)
  def streamB: Observable[U]
  def reactB(t: U)

  def streamError(error: Throwable, stream: Observable[_]) = {}
  def streamComplete(stream: Observable[_]) = {}

  lazy val wrappedA =
    streamA.materialize.map { notification: Notification[T] =>
      Wrapped(notification, streamA, reactA)
    }

  lazy val wrappedB =
    streamB.materialize.map { notification: Notification[U] =>
      Wrapped(notification, streamB, reactB)
    }

  private lazy val combined = wrappedA.merge(wrappedB)
  combined.subscribe(react _)

  private def react(msg: Wrapped[_]): Unit = msg match {
    case Wrapped(Notification.OnNext(value), stream, reaction) => reaction(value)
    case Wrapped(Notification.OnCompleted, stream, _)          => streamComplete(stream)
    case Wrapped(Notification.OnError(error), stream, _)       => streamError(error, stream)
  }
}