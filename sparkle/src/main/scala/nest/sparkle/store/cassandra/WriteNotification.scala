package nest.sparkle.store.cassandra

import rx.lang.scala.Observable
import akka.actor.ActorSystem
import java.util.concurrent.ConcurrentHashMap
import rx.lang.scala.Subject
import io.netty.util.internal.ConcurrentSet
import scala.collection.JavaConverters._
import scala.collection.mutable
import rx.lang.scala.Subscriber
import nest.sparkle.util.Log

abstract class ColumnUpdates[T]

case class ColumnUpdate[T](start: T, end: T) extends ColumnUpdates[T]

/** emitted when a subscribe has been received */
case class UpdateRegistered[T]() extends ColumnUpdates[T]

trait WriteListener {
  def listen[T](columnPath: String): Observable[ColumnUpdate[T]]
}

trait WriteNotifier {
  def notify[T](columnPath: String, columnUpdate: ColumnUpdate[T])
}

trait WriteListenNotify extends WriteListener with WriteNotifier

/** registry of columns written in the system */
class WriteNotification() extends WriteListenNotify with Log {
  val subjects = new ConcurrentHashMap[String, mutable.Set[Subject[ColumnUpdate[_]]]].asScala

  override def listen[T](columnPath: String): Observable[ColumnUpdate[T]] = {
    val subject = Subject[ColumnUpdate[T]]()
    val castSubject = subject.asInstanceOf[Subject[ColumnUpdate[_]]]
    val initialSet = mutable.Set(castSubject)
    val alreadySet = subjects.putIfAbsent(columnPath, initialSet)
    alreadySet.foreach { set => set += castSubject}
    // TODO emit UpdateRegistered 
    subject
  }

  override def notify[T](columnPath: String, columnUpdate: ColumnUpdate[T]) {
    subjects.get(columnPath).foreach { set =>
      log.trace(s"notifying $columnPath listeners of $columnUpdate")
      set.foreach { subject => subject.onNext(columnUpdate) }
    }
  }

  // TODO enable collection (and deletion from the map) of the subscribed/subjects 
  // if they are unused. ..Perhaps a timeout and a Subject wrapper that reports whether
  // there are active subscriptions..
}