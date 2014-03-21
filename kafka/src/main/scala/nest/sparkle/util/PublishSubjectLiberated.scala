package nest.sparkle.util

import rx.lang.scala.Subject


object PublishSubject {
  def apply[T](): PublishSubject[T] = new PublishSubject[T](rx.subjects.PublishSubject.create[T]())
}

class PublishSubject[T](val asJavaSubject: rx.subjects.PublishSubject[T]) extends Subject[T]  {}
