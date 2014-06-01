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

/** Message sent to WriteListener */
abstract class ColumnUpdates[T]

/** Sent to WriteListener, describes portion of data written */
case class ColumnUpdate[T](start: T, end: T) extends ColumnUpdates[T]

/** Sent to WriteListener, emitted when a subscribe is first registered */
case class UpdateRegistered[T]() extends ColumnUpdates[T]

/** api to listen for column updates on a given column path */
trait WriteListener {
  def listen[T](columnPath: String): Observable[ColumnUpdate[T]]
}

/** api to produce column updates on a given column path */
trait WriteNotifier {
  def notify[T](columnPath: String, columnUpdate: ColumnUpdate[T])
}

trait WriteListenNotify extends WriteListener with WriteNotifier

/** publish and subscribe for updates to column */
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
  
  // TODO route listen/notify remotely, probably over kafka
}